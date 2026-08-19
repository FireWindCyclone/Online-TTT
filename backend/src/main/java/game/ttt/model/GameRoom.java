package game.ttt.model;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import game.ttt.dto.PosDto;

public class GameRoom {
    private static final Logger log = LoggerFactory.getLogger(GameRoom.class);
    private volatile Optional<SseEmitter> player0 = Optional.empty();
    private volatile Optional<SseEmitter> player1 = Optional.empty();
    private Instant updatedAt = Instant.now(); // only used in scheduler thread so not volatile
    private int score0; // only used inside synchronized(room)
    private int score1; // only used inside synchronized(room)
    private Player playerTurn; // only used inside synchronized(room)
    private boolean gameOver = false; // only used inside synchronized(room)

    public GameRoom() {
        this.playerTurn = ThreadLocalRandom.current().nextBoolean() ? Player.PLAYER_0 : Player.PLAYER_1;
    }

    private Optional<SseEmitter> getPlayerEmitter(Player player) {
        return switch (player) {
            case PLAYER_0 -> player0;
            case PLAYER_1 -> player1;
        };
    }

    private void setPlayerEmitter(Player player, SseEmitter sse) {
        switch (player) {
            case PLAYER_0 -> player0 = Optional.ofNullable(sse);
            case PLAYER_1 -> player1 = Optional.ofNullable(sse);
        }
    }

    private void disconnectPlayer(Player player) {
        setPlayerEmitter(player, null);
        log.debug("Player {} disconnected", player);

        if (!gameOver) {
            sendConnectionStatus(player.getOtherPlayer(), "player-disconnected");
        }
    }

    private void sendConnectionStatus(Player player, String status) {
        log.debug("Notifying connection status {} to player {}", status, player);
        try {
            SseEmitter emitter = getPlayerEmitter(player).orElseThrow();
            emitter.send(SseEmitter.event().name(status));
        } catch (Exception ex) {
            log.debug("Failed to notify connection status {} to player {}: {}", status, player, ex.getMessage());
        }
    }

    public SseEmitter connectPlayer(Player player, String gameState) {
        SseEmitter sse = new SseEmitter(300000L); // 5 minutes

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

        setPlayerEmitter(player, sse);

        log.debug("Player {} sse created. Syncing initial game state {}", player, gameState);

        try {
            sendPlayerData(player, "sync", gameState);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to send game state " + gameState + " to player " + player, ex);
        }
        sendConnectionStatus(player.getOtherPlayer(), "player-connected");
        return sse;
    }

    public void syncPlayerMove(Player player, PosDto pos) {
        playerTurn = player;
        log.debug("Player {} syncing move {}", player, pos);
        try {
            sendPlayerData(player, "move", pos);
        } catch (Exception ex) {
            log.debug("Failed to sync move {} to player {}", pos, player);
        }
    }

    private void sendPlayerData(Player player, String eventName, Object data) throws IOException {
        SseEmitter emitter = getPlayerEmitter(player).orElseThrow();
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }

    public boolean isPlayerTurn(Player player) {
        return playerTurn == player;
    }

    public boolean canPlayerJoin(Player player) {
        return switch (player) {
            case PLAYER_0 -> player0.isEmpty();
            case PLAYER_1 -> player0.isPresent() && player1.isEmpty();
        };
    }

    public void pingPlayers() {
        log.debug("Pinging players");
        pingPlayer(Player.PLAYER_0);
        pingPlayer(Player.PLAYER_1);
    }

    private void pingPlayer(Player player) {
        try {
            SseEmitter emitter = getPlayerEmitter(player).orElseThrow();
            emitter.send(SseEmitter.event().comment("ping"));
            updatedAt = Instant.now();
        } catch (Exception ex) {
            log.warn("Failed to ping player {}: {}", player, ex.getMessage());
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

    public boolean isScoreFull() {
        return (score0 + score1) == 0b111111111;
    }

    public void finishGame(Player player, String finishStatus) {
        gameOver = true;
        sendGameOver(player, finishStatus);
        sendGameOver(player.getOtherPlayer(), finishStatus);
    }

    private void sendGameOver(Player player, String finishStatus) {
        log.debug("Sending game over status {} to player {}", finishStatus, player);
        try {
            SseEmitter emitter = getPlayerEmitter(player).orElseThrow();
            emitter.send(SseEmitter.event().name(finishStatus));
            emitter.complete();
        } catch (Exception ex) {
            log.debug("Failed to send game over status {} to player {}: {}", finishStatus, player, ex.getMessage());
        }
    }
}
