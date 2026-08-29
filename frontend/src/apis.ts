import board from "./board.ts";
import { exitGame, gameStatus } from "./main.ts";
import type { CellType, PlayerId, Pos, SyncData } from "./models.ts";
import { posIndex, session } from "./models.ts";

const API_URL: string = import.meta.env.VITE_API_URL ?? "/api";

export async function createGame(): Promise<string> {
	const res = await fetch(`${API_URL}/games`, {
		method: "POST",
		signal: AbortSignal.timeout(10000),
	});
	if (!res.ok) {
		throw new Error(`Failed to create game: ${res.status} ${await res.text()}`);
	}
	return res.text();
}

export async function joinGame(
	gameId: string,
	playerId: PlayerId,
): Promise<EventSource> {
	return new Promise((resolve, reject) => {
		const sse = new EventSource(`${API_URL}/games/${gameId}/join/${playerId}`);
		let hasSynced = false;

		sse.addEventListener("sync", (event: MessageEvent) => {
			hasSynced = true;
			board.online = true;
			const data: SyncData = JSON.parse(event.data);
			console.log(data);
			board.syncBoard(data);
			console.log(`Synced game: ${gameId}`);
			board.setMoveStatus();
			resolve(sse);
		});

		sse.addEventListener("move", (event: MessageEvent) => {
			const data: { pos: Pos; type: CellType } = JSON.parse(event.data);

			console.log("Making move");
			console.log(data);
			board.applyMove(posIndex.toIndex(data.pos), data.type);
			board.turn = true;
			board.setMoveStatus();
		});

		sse.addEventListener("won", (event: MessageEvent) => {
			const data: { type: CellType; cells: number[] } = JSON.parse(event.data);
			console.log(`${data.type} won. Marking won cells ${data.cells}`);
			board.win(data.type, data.cells);
			exitGame();
		});

		sse.addEventListener("draw", () => {
			console.log("Game Draws");
			gameStatus.textContent = "It's a draw";
			exitGame();
		});

		sse.addEventListener("player-connected", () => {
			console.log("Other player connected");
		});

		sse.addEventListener("player-disconnected", () => {
			console.log("Other player disconnected");
		});

		sse.onopen = () => console.log(`Player ${playerId} connected`);

		sse.onerror = () => {
			if (sse.readyState !== EventSource.CLOSED) {
				console.warn("SSE error. Reconnecting");
				return;
			}
			if (hasSynced) {
				console.log("SSE connection error. Exiting game");
				exitGame();
			} else {
				reject(new Error(`Failed to join game: ${gameId}`));
			}
		};
	});
}

export async function makeMove(pos: Pos): Promise<void> {
	const res = await fetch(
		`${API_URL}/games/${session.gameId}/move/${session.playerId}`,
		{
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify(pos),
		},
	);
	if (!res.ok) {
		throw new Error(
			`Failed to make move: ${pos} ${res.status} ${await res.text()}`,
		);
	}
}
