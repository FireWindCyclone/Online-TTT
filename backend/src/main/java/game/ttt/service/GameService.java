package game.ttt.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import game.ttt.dto.PosDto;
import game.ttt.entity.BoardInfo;
import game.ttt.exception.PlayerException;
import game.ttt.model.GameRoom;
import game.ttt.model.Player;
import game.ttt.exception.GameNotFoundException;
import game.ttt.repository.GameRepository;

@Service
public class GameService {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);
    private final GameRepository gameRepo;
    private final Map<String, GameRoom> gameRooms = new ConcurrentHashMap<>();
    private final TransactionTemplate transactionTemplate;

    public GameService(GameRepository gameRepo, TransactionTemplate transactionTemplate) {
        this.gameRepo = gameRepo;
        this.transactionTemplate = transactionTemplate;
    }

    public void makeMove(String gameId, Player player, PosDto pos) {
        GameRoom room = getRoom(gameId);
        synchronized (room) {
            if (!room.isPlayerTurn(player)) {
                throw new PlayerException("Not the player " + player + " turn yet in game " + gameId);
            }
            log.debug("Player {} turn. Making move", player);

            transactionTemplate.executeWithoutResult(_ -> {
                BoardInfo boardInfo = gameRepo.findById(gameId)
                        .orElseThrow(() -> new GameNotFoundException(
                                "Can't make move. Game with ID " + gameId + " doesn't exist"));

                String board = boardInfo.getBoard();
                if (board.charAt(pos.index()) != '*') {
                    throw new PlayerException(
                            "Cannot mark position " + pos + " because its already filled in game " + gameId);
                }
                log.debug("Move {} is valid", pos);

                char[] boardArr = board.toCharArray();
                boardArr[pos.index()] = room.getPlayerSymbol(player);
                boardInfo.setBoard(new String(boardArr));
                gameRepo.save(boardInfo);

            });

            room.makePlayerMove(player, pos);
        }
    }

    public SseEmitter createConnection(String gameId, Player player) {
        GameRoom room = getRoom(gameId);
        synchronized (room) {
            if (!room.canPlayerJoin(player)) {
                throw new PlayerException("Player " + player + " can't join game " + gameId);
            }
            return room.connectPlayer(player, showGame(gameId, false));
        }
    }

    private GameRoom getRoom(String gameId) {
        GameRoom room = gameRooms.get(gameId);
        if (room == null) {
            throw new GameNotFoundException("Game with ID " + gameId + " doesn't exist");
        }
        log.debug("Game room exists");
        return room;
    }

    public String createGameId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder id = new StringBuilder();
        SecureRandom rand = new SecureRandom();
        for (int i = 0; i < 4; i++) {
            id.append(chars.charAt(rand.nextInt(chars.length())));
        }
        String gameId = id.toString();
        gameRepo.save(new BoardInfo(gameId));
        gameRooms.put(gameId, new GameRoom());
        return gameId;
    }

    public String showGame(String gameId, boolean multiLine) {
        String board = gameRepo.findById(gameId)
                .orElseThrow(
                        () -> new GameNotFoundException("Can't show game. Game with ID " + gameId + " doesn't exist"))
                .getBoard();

        return multiLine ? String.join("\n", board.subSequence(0, 3), board.subSequence(3, 6), board.subSequence(6, 9))
                : board;

    }

    @Scheduled(fixedRate = 15000)
    public void checkClientPresent() {
        gameRooms.entrySet().removeIf(entry -> {
            if (Duration.between(entry.getValue().getLastRoomUpdate(), Instant.now()).toSeconds() > 60) {
                MDC.put("gameId", entry.getKey());
                log.info("Removing game");
                try {
                    gameRepo.deleteById(entry.getKey());
                    return true;
                } catch (Exception ex) {
                    log.debug("Failed to remove game");
                } finally {
                    MDC.remove("gameId");
                }
            }
            return false;
        });
        gameRooms.forEach((gameId, room) -> {
            MDC.put("gameId", gameId);
            room.pingPlayers();
            MDC.remove("gameId");
        });
    }
}
