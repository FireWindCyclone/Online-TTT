package game.ttt.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import game.ttt.dto.UpdatePosDto;
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
    private static final List<Integer> WIN_SCORES = List.of(7, 56, 448, 73, 146, 292, 273, 84);

    public GameService(GameRepository gameRepo) {
        this.gameRepo = gameRepo;
    }

    public void makeMove(Long gameId, Player player, UpdatePosDto pos) {
        GameRoom room = gameRooms.get(gameId);
        if (room == null || room.getEmitter(player) == null || room.getEmitter(player.getOtherPlayer()) == null) {
            throw new PlayerException("Not enough players to play the game " + gameId);
        }

        log.debug("Both emitters for game {} are present", gameId);

        synchronized (room) {
            if (!room.isPlayerTurn(player)) {
                throw new PlayerException("Not the player " + player + " turn yet");
            }
            log.debug("Correct player {} making move for game {}", player, gameId);

            BoardInfo boardInfo = gameRepo.findById(gameId).get();
            String board = boardInfo.getBoard();
            if (board.charAt(pos.index()) != ' ') {
                throw new PlayerException(
                        "Cannot mark position " + pos + " because its already filled");
            }
            log.debug("Valid move for game {} and pos {}", gameId, pos);

            room.incPlayerScore(player, Math.powExact(2, pos.index()));
            char[] boardArr = board.toCharArray();
            boardArr[pos.index()] = player.getSymbol();
            boardInfo.setBoard(new String(boardArr));
            gameRepo.save(boardInfo);

            room.syncGame(player.getOtherPlayer(), pos);

            if (WIN_SCORES.stream().anyMatch(score -> (room.getPlayerScore(player) & score) == score)) {
                log.info("Player {} won in game {}", player, gameId);
                room.playerWon(player);
            }
        }
    }

    public SseEmitter createEmitter(Long gameId, Player player) {
        GameRoom room = gameRooms.computeIfAbsent(gameId, _ -> new GameRoom());
        synchronized (room) {
            SseEmitter sse = room.connectPlayer(player);
            try {
                sse.send(SseEmitter.event().name("sync").data(showGame(gameId)));
            } catch (Exception ex) {
                sse.completeWithError(ex);
                throw new RuntimeException("Failed to send game " + gameId + " state to player " + player);
            }
            return sse;
        }
    }

    public void checkGameId(Long gameId) {
        if (!gameRooms.containsKey(gameId)) {
            throw new GameNotFoundException("Game with ID " + gameId + " doesn't exist");
        }
        log.debug("Game {} exists", gameId);
    }

    public Long createGameId() {
        Long gameId = gameRepo.save(new BoardInfo()).getId();
        gameRooms.put(gameId, new GameRoom());
        return gameId;
    }

    public String showGame(Long gameId) {
        return gameRepo.findById(gameId).get().getBoard();
    }

    public void checkPlayerVacant(Long gameId, Player player) {
        if (!gameRooms.get(gameId).canPlayerJoin(player)) {
            throw new PlayerException("Player " + player + " can't join Game " + gameId);
        }
    }

    @Scheduled(fixedRate = 15000)
    private void checkClientPresent() {
        gameRooms.entrySet().removeIf(entry -> {
            if (Duration.between(entry.getValue().getLastRoomUpdate(), Instant.now()).toSeconds() > 30) {
                log.debug("Removing Game {}", entry.getKey());
                gameRepo.deleteById(entry.getKey());
                return true;
            }
            return false;
        });
        gameRooms.forEach((_, room) -> room.pingPlayers());
    }

}
