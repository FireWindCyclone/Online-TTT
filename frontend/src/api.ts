import type { PlayerId, SyncData } from "./board";
import board from "./board";

const API_URL: string = import.meta.env.VITE_API_URL ?? "/api";

export async function createGame(): Promise<string> {
	const res = await fetch(`${API_URL}/games`, { method: "POST" });
	if (!res.ok) {
		throw new Error(`Failed to create game: ${res.status} ${res.statusText}`);
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

		sse.onopen = () => console.log(`Player ${playerId} Connected`);

		sse.onerror = () => {
			if (sse.readyState !== EventSource.CLOSED) {
				console.warn("SSE Error. Reconnecting");
				return;
			}
			if (!hasSynced) {
				reject(new Error(`Failed to join game: ${gameId}`));
			} else {
				// cleanup
			}
		};
	});
}
