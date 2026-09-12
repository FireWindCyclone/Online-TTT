package game.ttt.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import game.ttt.dto.PlayerSession;
import game.ttt.dto.PosDto;
import game.ttt.exception.GameNotFoundException;
import game.ttt.model.Player;
import game.ttt.service.GameService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/games")
public class GameController {

    private static final Logger log = LoggerFactory.getLogger(GameController.class);
    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping("/{gameId}")
    public ResponseEntity<String> showGame(@PathVariable String gameId) {
        String board = gameService.showGame(gameId, true);
        return ResponseEntity.ok(board);
    }

    @PostMapping
    public ResponseEntity<String> createGame(HttpServletRequest request) {
        String gameId = gameService.createGameId();
        createAndSetSession(request, new PlayerSession(Player.PLAYER_0, gameId));
        MDC.put("gameId", gameId);
        log.info("Player {} created", Player.PLAYER_0);
        MDC.remove("gameId");
        return ResponseEntity.status(HttpStatus.CREATED).body(gameId);
    }

    @PostMapping("/move")
    public ResponseEntity<Void> playerMove(
            @RequestBody PosDto pos, HttpSession session) {
        PlayerSession playerSession = (PlayerSession) session.getAttribute("player");
        if (playerSession == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        MDC.put("gameId", playerSession.gameId());
        try {
            gameService.makeMove(playerSession.gameId(), playerSession.playerId(), pos);
            log.info("Player {} made move {}", playerSession.playerId(), pos);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        } finally {
            MDC.remove("gameId");
        }
    }

    @GetMapping({ "/join", "/join/{gameId}" })
    public ResponseEntity<SseEmitter> joinGame(@PathVariable(required = false) String gameId,
            HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        PlayerSession playerSession = (session != null) ? (PlayerSession) session.getAttribute("player") : null;
        if (playerSession == null) {
            if (gameId == null) {
                throw new GameNotFoundException("Missing game id");
            }
            playerSession = new PlayerSession(Player.PLAYER_1, gameId);
            session = createAndSetSession(request, playerSession);
        }

        if (gameId != null) {
            if (!playerSession.gameId().equals(gameId)) {
                playerSession = new PlayerSession(Player.PLAYER_1, gameId);
                session = createAndSetSession(request, playerSession);
            } else if (playerSession.playerId() == Player.PLAYER_0) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
        }

        MDC.put("gameId", playerSession.gameId());
        try

        {
            log.info("Player {} trying to join game", playerSession.playerId());
            SseEmitter sse = gameService.createConnection(playerSession, session.getId());
            log.info("Player {} joined", playerSession.playerId());

            return ResponseEntity.ok().header("Cache-Control", "no-cache").header("X-Accel-Buffering", "no").body(sse);
        } finally {
            MDC.remove("gameId");
        }
    }

    private HttpSession createAndSetSession(HttpServletRequest request, PlayerSession playerSession) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        session = request.getSession(true);
        session.setAttribute("player", playerSession);
        return session;
    }
}
