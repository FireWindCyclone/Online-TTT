package game.ttt.exception;

public class MissingPlayerException extends RuntimeException {
    public MissingPlayerException(String msg) {
        super(msg);
    }

}
