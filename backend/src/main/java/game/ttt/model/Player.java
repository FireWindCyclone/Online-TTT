package game.ttt.model;

public enum Player {
    PLAYER_0('X'), PLAYER_1('O');

    private final char symbol;

    Player(char symbol) {
        this.symbol = symbol;
    }

    public char getSymbol() {
        return symbol;
    }

    public Player getOtherPlayer() {
        return switch (this) {
            case PLAYER_0 -> PLAYER_1;
            case PLAYER_1 -> PLAYER_0;
        };
    }

    public static Player fromId(Integer playerId) {
        return switch (playerId) {
            case 0 -> PLAYER_0;
            case 1 -> PLAYER_1;
            default -> throw new IllegalArgumentException("Invalid player");
        };

    }
}
