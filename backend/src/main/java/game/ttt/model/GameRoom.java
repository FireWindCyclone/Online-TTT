package game.ttt.model;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import game.ttt.dto.UpdatePosDto;

public class GameRoom {
    private static final Logger log = LoggerFactory.getLogger(GameRoom.class);
    private AtomicReference<SseEmitter> player0 = new AtomicReference<>();
    private AtomicReference<SseEmitter> player1 = new AtomicReference<>();
    private Instant updatedAt = Instant.now();
    private int score0;
    private int score1;
    private Player playerTurn;

    public GameRoom() {
        this.playerTurn = ThreadLocalRandom.current().nextBoolean() ? Player.PLAYER_0 : Player.PLAYER_1;
    }

    public SseEmitter getEmitter(Player player) {
        return switch (player) {
            case PLAYER_0 -> player0.get();
            case PLAYER_1 -> player1.get();
        };
    }

    private void removeEmitter(Player player, SseEmitter oldSse) {
        log.debug("Removing player {} emitter", player);
        switch (player) {
            case PLAYER_0 -> player0.compareAndSet(oldSse, null);
            case PLAYER_1 -> player1.compareAndSet(oldSse, null);
        }

        if (getEmitter(player) == null) {
            log.debug("Player {} disconnected", player);
            sendDisconnect(player.getOtherPlayer());
        }

    }

    private void sendDisconnect(Player player) {
        SseEmitter emitter = getEmitter(player);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("player-disconnected"));
            } catch (Exception ex) {
                log.error("Failed to send disconnect to player {}", player);
                emitter.completeWithError(ex);
            }
        }
    }

    public SseEmitter addEmitter(Player player) {

        SseEmitter oldSse = getEmitter(player);
        if (oldSse != null) {
            log.debug("Player {} reconnecting. Closing old connection", player);
            oldSse.complete();
        }

        SseEmitter sse = new SseEmitter(300000L);

        sse.onCompletion(() -> {
            log.info("Completed connection for player {}", player);
            removeEmitter(player, sse);
        });
        sse.onError(ex -> {
            log.error(ex.getMessage());
            log.error("Errored connection for player {}", player);
            removeEmitter(player, sse);
        });
        sse.onTimeout(() -> {
            log.warn("Timed out connection for player {}", player);
            removeEmitter(player, sse);
        });

        switch (player) {
            case PLAYER_0 -> player0.set(sse);
            case PLAYER_1 -> player1.set(sse);
        }

        log.debug("Player {} emitter added", player);

        return sse;
    }

    public void syncGame(Player player, UpdatePosDto pos) {
        playerTurn = player;
        SseEmitter emitter = getEmitter(playerTurn);

        if (emitter == null) {
            log.warn("Player {} has disconnected. Skipping sync", playerTurn);
            return;
        }

        try {
            emitter.send(SseEmitter.event().name("move").data(pos));
        } catch (Exception ex) {
            log.error("Player {} sync failed. Removing player {}", playerTurn, playerTurn);
            emitter.completeWithError(ex);
            return;
        }
        log.debug("Player {} synced", playerTurn);
    }

    public boolean isPlayerTurn(Player player) {
        return playerTurn == player;
    }

    public boolean canPlayerJoin(Player player) {
        return switch (player) {
            case PLAYER_0 -> player0.get() == null;
            case PLAYER_1 -> player0.get() != null && player1.get() == null;
        };
    }

    public void pingPlayers() {
        pingPlayer(Player.PLAYER_0);
        pingPlayer(Player.PLAYER_1);
    }

    private void pingPlayer(Player player) {
        SseEmitter emitter = getEmitter(player);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception ex) {
                log.error("Failed to ping player");
                emitter.completeWithError(ex);
            }
            updatedAt = Instant.now();
        }
    }

    public Instant getLastRoomUpdate() {
        return updatedAt;
    }

    public int getPlayerScore(Player player) {
        return switch (player) {
            case PLAYER_0 -> score0;
            case PLAYER_1 -> score1;
        };
    }

    public void incPlayerScore(Player player, int inc) {
        switch (player) {
            case PLAYER_0 -> score0 += inc;
            case PLAYER_1 -> score1 += inc;
        }
    }

    public void playerWon(Player player) {
        String winEvent = "won-" + player.getSymbol();
        sendWinner(player, winEvent);
        sendWinner(player.getOtherPlayer(), winEvent);
    }

    private void sendWinner(Player player, String winEvent) {
        SseEmitter emitter = getEmitter(player);
        if (emitter != null) {
            log.debug("Sending winner to player {}", player);
            try {
                emitter.send(SseEmitter.event().name(winEvent));
                emitter.complete();
            } catch (Exception ex) {
                log.error("Failed to send winner to player {}", player);
                emitter.completeWithError(ex);
                return;
            }
        }
    }
}
