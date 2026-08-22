import board from "./board.ts";
import { exitGame, gameStatus } from "./main.ts";
import type { CellType, PlayerId, Pos, SyncData } from "./models.ts";
import { posIndex, session } from "./models.ts";

const API_URL: string = import.meta.env.VITE_API_URL ?? "/api";

export async function createGame(): Promise<string> {
	const res = await fetch(`${API_URL}/games`, { method: "POST" });
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
			const data: SyncData = JSON.parse(event.data);
			console.log(data);
			board.syncBoard(data);
			console.log(`Synced game: ${gameId}`);
			resolve(sse);
		});
		sse.addEventListener("move", (event: MessageEvent) => {
			const data: { pos: Pos; type: CellType } = JSON.parse(event.data);

			console.log("Making move");
			console.log(data);
			board.applyMove(posIndex.toIndex(data.pos), data.type);
			board.setTurn(true);
		});
		sse.addEventListener("won-x", (event: MessageEvent) => {
			const cells = JSON.parse(event.data);
			console.log(`X won. Marking won cells ${cells}`);
			board.win("X", cells);
			exitGame();
		});
		sse.addEventListener("won-o", (event: MessageEvent) => {
			const cells = JSON.parse(event.data);
			console.log(`O won. Marking won cells ${cells}`);
			board.win("O", cells);
			exitGame();
		});
		sse.addEventListener("draw", () => {
			console.log("Game Draws");
			gameStatus!.textContent = "Its a draw";
			exitGame();
		});
		sse.addEventListener("player-connected", (event: MessageEvent) => {});
		sse.addEventListener("player-disconnected", (event: MessageEvent) => {});

		sse.onopen = () => console.log(`Player ${playerId} Connected`);

		sse.onerror = () => {
			if (sse.readyState !== EventSource.CLOSED) {
				console.warn("SSE Error. Reconnecting");
				return;
			}
			if (hasSynced) {
				console.log("Error. Exiting Game");
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
