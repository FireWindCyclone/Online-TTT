package game.ttt.model;

import java.io.IOException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import game.ttt.dto.PosDto;
import game.ttt.dto.ScoreDto;

public class GameRoom {
    private static final Logger log = LoggerFactory.getLogger(GameRoom.class);
    private volatile Optional<SseEmitter> player0 = Optional.empty();
    private volatile Optional<SseEmitter> player1 = Optional.empty();
    private Instant updatedAt = Instant.now(); // only used in scheduler thread so not volatile
    private final Map<Player, ScoreDto> scores = new EnumMap<>(Player.class); // only used inside synchronized(room)
    private Player playerTurn; // only used inside synchronized(room)
    private boolean gameOver = false; // only used inside synchronized(room)
    private final Map<Player, Character> symbols;

    public GameRoom() {
        scores.put(Player.PLAYER_0, new ScoreDto(0));
        scores.put(Player.PLAYER_1, new ScoreDto(0));

        this.playerTurn = ThreadLocalRandom.current().nextBoolean() ? Player.PLAYER_0 : Player.PLAYER_1;

        boolean symbol = ThreadLocalRandom.current().nextBoolean();
        this.symbols = Map.of(Player.PLAYER_0, symbol ? 'X' : 'O', Player.PLAYER_1, symbol ? 'O' : 'X');
    }

    private Optional<SseEmitter> getPlayerEmitter(Player player) {
        return player == Player.PLAYER_0 ? player0 : player1;
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
            sendPlayerEvent(player, status, null);
        } catch (Exception ex) {
            log.debug("Failed to notify connection status {} to player {}: {}", status, player, ex.getMessage());
        }
    }

    public SseEmitter connectPlayer(Player player, String board) {
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

        log.debug("Player {} sse created. Syncing initial game state {}", player, board);

        try {
            sendPlayerEvent(player, "sync",
                    Map.of("board", board, "playerType", symbols.get(player), "playerTurn", player == playerTurn));
        } catch (Exception ex) {
            throw new RuntimeException("Failed to send game state " + board + " to player " + player, ex);
        }
        sendConnectionStatus(player.getOtherPlayer(), "player-connected");
        return sse;
    }

    private void syncPlayerMove(Player player, Object moveData) {
        playerTurn = player;
        log.debug("Player {} syncing move {}", player, moveData);
        try {
            sendPlayerEvent(player, "move", moveData);
        } catch (Exception ex) {
            log.debug("Failed to sync move {} to player {}", moveData, player);
        }
    }

    private SseEmitter sendPlayerEvent(Player player, String eventName, Object eventData) throws IOException {
        SseEmitter sse = getPlayerEmitter(player).orElseThrow();
        if (eventData == null) {
            sse.send(SseEmitter.event().name(eventName));
        } else {
            sse.send(SseEmitter.event().name(eventName).data(eventData));
        }
        return sse;
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
            SseEmitter sse = getPlayerEmitter(player).orElseThrow();
            sse.send(SseEmitter.event().comment("ping"));
            updatedAt = Instant.now();
        } catch (Exception ex) {
            log.warn("Failed to ping player {}: {}", player, ex.getMessage());
        }
    }

    public Instant getLastRoomUpdate() {
        return updatedAt;
    }

    private void finishGame(String eventName, Object eventData) {
        gameOver = true;
        sendGameOver(Player.PLAYER_0, eventName, eventData);
        sendGameOver(Player.PLAYER_1, eventName, eventData);
    }

    private void sendGameOver(Player player, String eventName, Object eventData) {
        log.debug("Sending game over event {} to player {}", eventName, player);
        try {
            sendPlayerEvent(player, eventName, eventData).complete();
        } catch (Exception ex) {
            log.debug("Failed to send game over event {} to player {}: {}", eventName, player, ex.getMessage());
        }
    }

    public void makePlayerMove(Player player, PosDto pos) {
        log.debug("Player {} making move {}", player, pos);
        ScoreDto newScore = scores.get(player).update(pos);
        scores.put(player, newScore);

        syncPlayerMove(player.getOtherPlayer(), Map.of("pos", pos, "type", symbols.get(player)));

        Optional<ScoreDto> winScore = newScore.match();
        winScore.ifPresent((score) -> {
            log.info("Player {} won. Game over", player);
            String winStatus = "won-" + Character.toLowerCase(symbols.get(player));
            finishGame(winStatus, score.list());
        });

        if (!gameOver && ScoreDto.combine(scores.get(Player.PLAYER_0), scores.get(Player.PLAYER_1)).isFull()) {
            log.info("Player {} draws. Game over", player);
            finishGame("draw", null);
        }
    }

    public char getPlayerSymbol(Player player) {
        return symbols.get(player);
    }
}
