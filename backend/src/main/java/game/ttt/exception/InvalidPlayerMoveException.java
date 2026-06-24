package game.ttt.exception;

public class InvalidPlayerMoveException extends RuntimeException {

    public InvalidPlayerMoveException(String msg) {
        super(msg);
    }
}
