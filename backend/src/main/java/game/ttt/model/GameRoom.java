package game.ttt.model;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import game.ttt.dto.PosDto;

public class GameRoom {
    private static final Logger log = LoggerFactory.getLogger(GameRoom.class);
    private volatile SseEmitter player0;
    private volatile SseEmitter player1;
    private Instant updatedAt = Instant.now(); // only used in scheduler thread so not volatile
    private int score0; // only used inside synchronized(room)
    private int score1; // only used inside synchronized(room)
    private Player playerTurn; // only used inside synchronized(room)
    private boolean gameOver = false; // only used inside synchronized(room)

    public GameRoom() {
        this.playerTurn = ThreadLocalRandom.current().nextBoolean() ? Player.PLAYER_0 : Player.PLAYER_1;
    }

    public SseEmitter getEmitter(Player player) {
        return switch (player) {
            case PLAYER_0 -> player0;
            case PLAYER_1 -> player1;
        };
    }

    private void disconnectPlayer(Player player) {
        switch (player) {
            case PLAYER_0 -> player0 = null;
            case PLAYER_1 -> player1 = null;
        }
        log.debug("Player {} disconnected", player);

        if (!gameOver) {
            log.debug("Notifying {}", player.getOtherPlayer());
            sendDisconnect(player.getOtherPlayer());
        }
    }

    private void sendDisconnect(Player player) {
        SseEmitter emitter = getEmitter(player);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("player-disconnected"));
            } catch (Exception ex) {
                log.debug("Failed to notify disconnect to player {}: {}", player, ex.getMessage());
            }
        }
    }

    public SseEmitter connectPlayer(Player player) {
        SseEmitter sse = new SseEmitter(300000L);

        sse.onCompletion(() -> {
            log.debug("Completed connection for player {}", player);
            disconnectPlayer(player);
        });
        sse.onError(ex -> {
            log.warn("Errored connection for player {}: {}", player, ex.getMessage());
        });
        sse.onTimeout(() -> {
            log.warn("Timed out connection for player {}", player);
        });

        switch (player) {
            case PLAYER_0 -> player0 = sse;
            case PLAYER_1 -> player1 = sse;
        }

        return sse;
    }

    public void syncGame(Player player, PosDto pos) {
        playerTurn = player;
        SseEmitter emitter = getEmitter(player);

        if (emitter == null) {
            log.debug("Player {} has disconnected. Can't sync to player {}", player, player);
            return;
        }

        log.debug("Player {} syncing pos {}", player, pos);
        try {
            emitter.send(SseEmitter.event().name("move").data(pos));
        } catch (Exception ex) {
            log.debug("Player {} sync failed: {}", player, ex.getMessage());
        }
    }

    public boolean isPlayerTurn(Player player) {
        return playerTurn == player;
    }

    public boolean canPlayerJoin(Player player) {
        return switch (player) {
            case PLAYER_0 -> player0 == null;
            case PLAYER_1 -> player0 != null && player1 == null;
        };
    }

    public void pingPlayers() {
        log.debug("Pinging players");
        pingPlayer(Player.PLAYER_0);
        pingPlayer(Player.PLAYER_1);
    }

    private void pingPlayer(Player player) {
        SseEmitter emitter = getEmitter(player);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception ex) {
                log.debug("Failed to ping player {}: {}", player, ex.getMessage());
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
        gameOver = true;
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
                log.debug("Failed to send winner to player {}: {}", player, ex.getMessage());
            }
        }
    }
}
