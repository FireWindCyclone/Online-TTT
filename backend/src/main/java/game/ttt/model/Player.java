package game.ttt.model;

public enum Player {
    PLAYER_0, PLAYER_1;

    public static Player fromId(Integer playerId) {
        return switch (playerId) {
            case 0 -> PLAYER_0;
            case 1 -> PLAYER_1;
            default -> throw new IllegalArgumentException("Invalid player");
        };

    }
}
