package game.ttt.model;

public enum Player {
    PLAYER_0, PLAYER_1;

    public Player getOtherPlayer() {
        return this == PLAYER_0 ? PLAYER_1 : PLAYER_0;
    };
}
