export type PlayerId = 0 | 1;

export type Session = {
	gameId: string | null;
	sse: EventSource | null;
	reset: () => void;
};

export const session: Session = {
	gameId: null,
	sse: null,
	reset() {
		this.gameId = null;
		this.sse = null;
	},
};

export type CellType = "X" | "O";

export type SyncData = {
	board: string;
	playerType: CellType;
	playerTurn: boolean;
	gameOver: boolean;
};

export type Pos = {
	row: number;
	col: number;
};
