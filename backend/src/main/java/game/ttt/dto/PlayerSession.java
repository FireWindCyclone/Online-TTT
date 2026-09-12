package game.ttt.dto;

import game.ttt.model.Player;

public record PlayerSession(Player playerId, String gameId) {
}
