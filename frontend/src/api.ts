const API_URL: string = import.meta.env.VITE_API_URL ?? "/api";

export async function createGame(): Promise<string> {
	const res = await fetch(`${API_URL}/games`, { method: "POST" });
	if (!res.ok) {
		throw new Error(`Failed to create game: ${res.status} ${res.statusText}`);
	}
	return res.text();
}
