package game.ttt.service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import game.ttt.dto.UpdatePosDto;
import game.ttt.entity.BoardInfo;
import game.ttt.exception.MissingPlayerException;
import game.ttt.model.GameRoom;
import game.ttt.model.Player;
import game.ttt.exception.GameNotFoundException;
import game.ttt.exception.InvalidPlayerMoveException;
import game.ttt.exception.InvalidPlayerTurnException;
import game.ttt.repository.GameRepository;

@Service
public class GameService {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);
    private final GameRepository gameRepo;
    private final Map<Long, GameRoom> gameRooms = new ConcurrentHashMap<>();

    public GameService(GameRepository gameRepo) {
        this.gameRepo = gameRepo;
    }

    public void makeMove(Long gameId, Player player, UpdatePosDto pos) {
        GameRoom room = gameRooms.get(gameId);
        if (room == null || room.getEmitter(Player.PLAYER_0) == null || room.getEmitter(Player.PLAYER_1) == null) {
            throw new MissingPlayerException("Not enough players to play the game " + gameId);
        }

        log.debug("Both emitters for game {} are present", gameId);

        synchronized (room) {
            if (!room.isPlayerTurn(player)) {
                throw new InvalidPlayerTurnException("Not the player " + player + " turn yet");
            }
            log.debug("Correct player {} making move for game {}", player, gameId);

            BoardInfo boardInfo = gameRepo.findById(gameId).get();
            String board = boardInfo.getBoard();
            if (board.charAt(pos.index()) != ' ') {
                throw new InvalidPlayerMoveException(
                        "Cannot mark position " + pos + " because its already filled");
            }
            log.debug("Valid move for game {} and pos {}", gameId, pos);

            char[] boardArr = board.toCharArray();
            boardArr[pos.index()] = player == Player.PLAYER_0 ? 'X' : 'O';
            boardInfo.setBoard(new String(boardArr));
            gameRepo.save(boardInfo);

            room.syncGame(player, pos);
        }
    }

    public SseEmitter createEmitter(Long gameId, Player player) {
        GameRoom room = gameRooms.computeIfAbsent(gameId, _ -> new GameRoom());
        synchronized (room) {
            SseEmitter oldSse = room.getEmitter(player);

            if (oldSse != null) {
                log.debug("Player {} reconnecting in game {}", player, gameId);
                oldSse.complete();
            }

            SseEmitter newSse = room.addEmitter(player);

            try {
                newSse.send(SseEmitter.event().name("sync").data(showGame(gameId)));
            } catch (Exception ex) {
                newSse.completeWithError(ex);
                throw new RuntimeException("Failed to send game state to player " + player, ex);
            }

            return newSse;
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

    public Integer getVacantPlayerId(Long gameId) {
        return gameRooms.get(gameId).getVacantPlayerId();
    }

}
