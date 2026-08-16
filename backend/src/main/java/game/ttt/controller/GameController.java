package game.ttt.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import game.ttt.dto.PosDto;
import game.ttt.model.Player;
import game.ttt.service.GameService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
public class GameController {

    private static final Logger log = LoggerFactory.getLogger(GameController.class);
    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping("/games/{gameId}/show")
    public ResponseEntity<String> showGame(@PathVariable Long gameId) {
        String board = gameService.showGame(gameId);
        return ResponseEntity.ok(board);
    }

    @PostMapping("/games")
    public ResponseEntity<Long> createGame() {
        Long gameId = gameService.createGameId();
        log.info("Player {} created game {}", Player.PLAYER_0, gameId);
        return ResponseEntity.status(HttpStatus.CREATED).body(gameId);
    }

    @PatchMapping("/games/{gameId}/move/{playerId}")
    public ResponseEntity<Void> playerMove(@PathVariable Long gameId, @PathVariable Integer playerId,
            @RequestBody PosDto pos) {

        Player player = Player.fromId(playerId);
        gameService.makeMove(gameId, player, pos);
        log.info("Player {} made move {} in game {}", player, pos, gameId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/games/{gameId}/join/{playerId}")
    public ResponseEntity<SseEmitter> joinGame(@PathVariable Long gameId, @PathVariable Integer playerId) {
        Player player = Player.fromId(playerId);
        SseEmitter sse = gameService.createConnection(gameId, player);
        log.info("Player {} joined game {}", player, gameId);

        return ResponseEntity.ok().header("Cache-Control", "no-cache").header("X-Accel-Buffering", "no").body(sse);
    }

}
