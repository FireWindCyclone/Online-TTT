package game.ttt.advice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import game.ttt.exception.InvalidPlayerTurnException;
import game.ttt.exception.MissingPlayerException;
import game.ttt.exception.GameNotFoundException;
import game.ttt.exception.InvalidPlayerMoveException;

@RestControllerAdvice
public class GameExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GameExceptionHandler.class);

    @ExceptionHandler(GameNotFoundException.class)
    public ResponseEntity<String> handleGameNotFound(GameNotFoundException ex) {
        log.error(ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(InvalidPlayerTurnException.class)
    public ResponseEntity<String> handleInvalidPlayerTurn(InvalidPlayerTurnException ex) {
        log.error(ex.getMessage());
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(InvalidPlayerMoveException.class)
    public ResponseEntity<String> handleInvalidPlayerMove(InvalidPlayerMoveException ex) {
        log.error(ex.getMessage());
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(MissingPlayerException.class)
    public ResponseEntity<String> handleMissingPlayer(MissingPlayerException ex) {
        log.error(ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

}
