package game.ttt.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
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
    private final Map<Long, GameRoom> gameRooms = new ConcurrentHashMap<>();
    private static final int[] WIN_SCORES = { 7, 56, 448, 73, 146, 292, 273, 84 };

    public GameService(GameRepository gameRepo) {
        this.gameRepo = gameRepo;
    }

    public void makeMove(Long gameId, Player player, PosDto pos) {
        GameRoom room = getRoom(gameId);
        synchronized (room) {
            if (!room.isPlayerTurn(player)) {
                throw new PlayerException("Not the player " + player + " turn yet");
            }
            log.debug("Correct player {} making move for game {}", player, gameId);

            BoardInfo boardInfo = gameRepo.findById(gameId)
                    .orElseThrow(() -> new GameNotFoundException(
                            "Can't make move. Game with ID " + gameId + " doesn't exist"));
            String board = boardInfo.getBoard();
            if (board.charAt(pos.index()) != ' ') {
                throw new PlayerException(
                        "Cannot mark position " + pos + " because its already filled");
            }
            log.debug("Valid move for game {} and pos {}", gameId, pos);

            char[] boardArr = board.toCharArray();
            boardArr[pos.index()] = player.getSymbol();
            boardInfo.setBoard(new String(boardArr));
            gameRepo.save(boardInfo);
            room.incPlayerScore(player, 1 << pos.index());
            room.syncPlayerMove(player.getOtherPlayer(), pos);

            if (Arrays.stream(WIN_SCORES).anyMatch(score -> (room.getPlayerScore(player) & score) == score)) {
                log.info("Player {} won in game {}", player, gameId);
                String winStatus = "won-" + Character.toLowerCase(player.getSymbol());
                room.finishGame(player, winStatus);
                return;
            }
            if (room.isScoreFull()) {
                log.info("Player {} draws game {}", player, gameId);
                room.finishGame(player, "draw");
            }
        }
    }

    public SseEmitter createConnection(Long gameId, Player player) {
        GameRoom room = getRoom(gameId);
        synchronized (room) {
            if (!room.canPlayerJoin(player)) {
                throw new PlayerException("Player " + player + " can't join Game " + gameId);
            }
            return room.connectPlayer(player, showGame(gameId));
        }
    }

    private GameRoom getRoom(Long gameId) {
        GameRoom room = gameRooms.get(gameId);
        if (room == null) {
            throw new GameNotFoundException("Game with ID " + gameId + " doesn't exist");
        }
        log.debug("Game {} exists", gameId);
        return room;
    }

    public Long createGameId() {
        Long gameId = gameRepo.save(new BoardInfo()).getId();
        gameRooms.put(gameId, new GameRoom());
        return gameId;
    }

    public String showGame(Long gameId) {
        return gameRepo.findById(gameId)
                .orElseThrow(
                        () -> new GameNotFoundException("Can't show game. Game with ID " + gameId + " doesn't exist"))
                .getBoard();
    }

    @Scheduled(fixedRate = 15000)
    public void checkClientPresent() {
        gameRooms.entrySet().removeIf(entry -> {
            if (Duration.between(entry.getValue().getLastRoomUpdate(), Instant.now()).toSeconds() > 60) {
                log.debug("Removing Game {}", entry.getKey());
                gameRepo.deleteById(entry.getKey()); // can throw
                return true;
            }
            return false;
        });
        gameRooms.forEach((_, room) -> room.pingPlayers());
    }

}
