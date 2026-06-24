package game.ttt.exception;

public class InvalidPlayerTurnException extends RuntimeException {
    public InvalidPlayerTurnException(String msg) {
        super(msg);
    }
}
