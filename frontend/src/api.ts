import type { CellType, PlayerId, Pos, SyncData } from "./board";
import board, { posIndex } from "./board";

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

			console.log(`Making move: ${data}`);
			board.applyMove(posIndex.toIndex(data.pos), data.type);
			board.setTurn(true);
		});

		sse.onopen = () => console.log(`Player ${playerId} Connected`);

		sse.onerror = () => {
			if (sse.readyState !== EventSource.CLOSED) {
				console.warn("SSE Error. Reconnecting");
				return;
			}
			if (!hasSynced) {
				reject(new Error(`Failed to join game: ${gameId}`));
			} else {
				// TODO cleanup
			}
		};
	});
}
export async function makeMove(
	gameId: string,
	playerId: PlayerId,
	pos: Pos,
): Promise<void> {
	const res = await fetch(`${API_URL}/games/${gameId}/move/${playerId}`, {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify(pos),
	});
	if (!res.ok) {
		throw new Error(
			`Failed to make move: ${pos} ${res.status} ${await res.text()}`,
		);
	}
}
