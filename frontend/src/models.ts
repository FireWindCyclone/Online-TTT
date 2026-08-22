export type PlayerId = 0 | 1;

export type Session = {
	playerId: PlayerId | null;
	gameId: string | null;
	sse: EventSource | null;
};

export const session: Session = {
	playerId: null,
	gameId: null,
	sse: null,
};

export type CellType = "X" | "O";

export type SyncData = {
	board: string;
	playerType: CellType;
	playerTurn: boolean;
};

export type Pos = {
	row: number;
	col: number;
};

export const posIndex = {
	MAX_COLS: 3 as const,
	toIndex(pos: Pos): number {
		return pos.row * this.MAX_COLS + pos.col;
	},
	toPos(idx: number): Pos {
		return { row: Math.floor(idx / this.MAX_COLS), col: idx % this.MAX_COLS };
	},
};
