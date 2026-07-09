package game.ttt.model;

import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import game.ttt.dto.UpdatePosDto;

public class GameRoom {
    private static final Logger log = LoggerFactory.getLogger(GameRoom.class);
    private volatile SseEmitter player0;
    private volatile SseEmitter player1;
    private int score0;
    private int score1;
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
            case PLAYER_0 -> player0 = sse;
            case PLAYER_1 -> player1 = sse;
        }

        log.debug("Player {} emitter added", player);

        return sse;
    }

    public void syncGame(Player player, UpdatePosDto pos) {
        playerTurn = player.getOtherPlayer();
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
            case PLAYER_0 -> player0 == null;
            case PLAYER_1 -> player0 != null && player1 == null;
        };
    }

    public void pingPlayers() {
        pingPlayer(player0, player1);
        pingPlayer(player1, player0);
    }

    private void pingPlayer(SseEmitter player, SseEmitter playerOther) {
        if (player != null) {
            try {
                if (playerOther == null) {
                    player.send(SseEmitter.event().name("player-disconnected"));
                    log.debug("The other player has disconnected");
                } else {
                    player.send(SseEmitter.event().comment("ping"));
                }
            } catch (Exception ex) {
                log.error("Failed to ping player. Removing player");
                player.completeWithError(ex);
            }
        }
    }

    public boolean isEmpty() {
        return player0 == null && player1 == null;
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
