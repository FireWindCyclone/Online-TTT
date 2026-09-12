import board from "./board";
import { exitGame, gameStatus } from "./main";
import { type CellType, type Pos, type SyncData, session } from "./models";
import showToast from "./toasts";
import { posIndex } from "./utils";

const API_URL: string = import.meta.env.VITE_API_URL ?? "/api";

export async function createGame(): Promise<string> {
	const res = await fetch(`${API_URL}/games`, {
		method: "POST",
		credentials: "include",
		signal: AbortSignal.timeout(7000),
	});
	if (!res.ok) {
		throw new Error(`Failed to create game: ${res.status} ${await res.text()}`);
	}
	return res.text();
}

export async function joinGame(gameId?: string): Promise<EventSource> {
	return new Promise((resolve, reject) => {
		let url = `${API_URL}/games/join`;
		if (gameId) {
			url += `/${gameId}`;
		}

		const sse = new EventSource(url, {
			withCredentials: true,
		});
		let hasSynced = false;

		sse.addEventListener("sync", (event: MessageEvent) => {
			hasSynced = true;
			board.online = true;
			const data: SyncData = JSON.parse(event.data);
			console.log(data);
			board.syncBoard(data);
			console.log(`Synced game: ${session.gameId}`);
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
			showToast("Other player connected");
			console.log("Other player connected");
		});

		sse.addEventListener("player-disconnected", () => {
			showToast("Other player disconnected");
			console.log("Other player disconnected");
		});

		sse.onopen = () => console.log("Player connected");

		sse.onerror = async () => {
			if (sse.readyState !== EventSource.CLOSED) {
				console.warn("SSE error. Reconnecting");
				return;
			}
			if (hasSynced) {
				console.log("SSE connection error. Reconnecting manually");
				session.sse = await joinGame();
			} else {
				reject(new Error(`Failed to join game: ${gameId}`));
			}
		};
	});
}

export async function makeMove(pos: Pos): Promise<void> {
	const res = await fetch(`${API_URL}/games/move`, {
		method: "POST",
		credentials: "include",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify(pos),
	});
	if (!res.ok) {
		throw new Error(
			`Failed to make move: ${pos} ${res.status} ${await res.text()}`,
		);
	}
}
