package game.ttt.model;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import game.ttt.dto.UpdatePosDto;
import game.ttt.exception.MissingPlayerException;

public class GameRoom {
    private static final Logger log = LoggerFactory.getLogger(GameRoom.class);
    private volatile SseEmitter player0;
    private volatile SseEmitter player1;
    private Player playerTurn;

    public GameRoom() {
        this.playerTurn = ThreadLocalRandom.current().nextBoolean() ? Player.PLAYER_0 : Player.PLAYER_1;
    }

    public SseEmitter getEmitter(Player player) {
        return switch (player) {
            case PLAYER_0 -> player0;
            case PLAYER_1 -> player1;
        };
    }

    private void removeEmitter(Player player, SseEmitter oldSse) {
        switch (player) {
            case PLAYER_0 -> {
                if (player0 == oldSse)
                    player0 = null;
            }
            case PLAYER_1 -> {
                if (player1 == oldSse)
                    player1 = null;
            }
        }
        log.debug("Player {} emitter removed", player);
    }

    public SseEmitter addEmitter(Player player) {
        SseEmitter sse = new SseEmitter(300000L);

        sse.onCompletion(() -> {
            log.info("Completed connection for player {}", player);
            removeEmitter(player, sse);
        });
        sse.onError(_ -> {
            log.error("Errored connection for player {}", player);
            removeEmitter(player, sse);
        });
        sse.onTimeout(() -> {
            log.warn("Timed out connection for player {}", player);
            removeEmitter(player, sse);
        });

        switch (player) {
            case PLAYER_0 -> player0 = sse;
            case PLAYER_1 -> player1 = sse;
        }

        log.debug("Player {} emitter added", player);

        return sse;
    }

    private SseEmitter switchPlayerTurn(Player player) {
        return switch (player) {
            case PLAYER_0 -> {
                playerTurn = Player.PLAYER_1;
                yield player1;
            }
            case PLAYER_1 -> {
                playerTurn = Player.PLAYER_0;
                yield player0;
            }
        };

    }

    public void syncGame(Player player, UpdatePosDto pos) {
        SseEmitter emitter = switchPlayerTurn(player);

        if (emitter == null) {
            log.warn("Player {} is disconnected. Skipping sync", playerTurn);
            return;
        }

        try {
            emitter.send(SseEmitter.event().name("move").data(pos));
        } catch (Exception ex) {
            emitter.completeWithError(ex);
            throw new RuntimeException("Failed Sync to player " + player, ex);
        }
        log.debug("Player {} synced", playerTurn);
    }

    public boolean isPlayerTurn(Player player) {
        return playerTurn == player;
    }

    public Integer getVacantPlayerId() {
        if (player0 == null) {
            return 0;
        }
        if (player1 == null) {
            return 1;
        }

        throw new MissingPlayerException("Game room is full");
    }

}
