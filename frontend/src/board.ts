import { makeMove } from "./api";

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

const CELL_CLASS: Record<CellType, string> = {
	X: "cell-x",
	O: "cell-o",
};

class Board {
	private readonly cells: NodeListOf<HTMLButtonElement> =
		document.querySelectorAll<HTMLButtonElement>(".cell")!;
	private readonly playerTypeDisplay =
		document.querySelector<HTMLUListElement>(".player-type")!;
	private readonly clearBtn: HTMLButtonElement =
		document.querySelector(".reset-game")!;
	private playerType: CellType = "X";
	private playerTurn: boolean = true;
	private online: boolean = false;

	constructor() {
		this.cells.forEach((cell) => {
			cell.addEventListener("click", this);
		});
		this.clearBtn?.addEventListener("click", (_) => this.resetCells());
	}

	applyMove(idx: number, moveType: CellType) {
		this.cells[idx].classList.add(CELL_CLASS[moveType]);
		this.cells[idx].disabled = true;
	}

	resetCells() {
		this.cells.forEach((cell) => {
			cell.className = "cell";
			cell.disabled = false;
		});
	}

	setTurn(turn: boolean) {
		this.playerTurn = turn;
	}

	syncBoard(data: SyncData) {
		this.playerType = data.playerType;
		this.playerTurn = data.playerTurn;
		for (const [i, c] of [...data.board].entries()) {
			if (c !== "*") {
				this.applyMove(i, c as CellType);
			}
		}
		if (this.playerType === "X") {
			return;
		}
		this.playerTypeDisplay.querySelector(
			".player-type>li:nth-child(1)",
		)!.textContent = "You (O)";
		this.playerTypeDisplay.querySelector(
			".player-type>li:nth-child(2)",
		)!.textContent = "Opponent (X)";
	}

	connected() {
		this.resetCells();
		this.online = true;
		this.clearBtn.hidden = true;
		this.playerTypeDisplay.hidden = false;
	}

	async handleEvent(event: Event) {
		if (!this.playerTurn) {
			return;
		}
		const cell = event.currentTarget as HTMLButtonElement;
		const idx = parseInt(cell.dataset.index!, 10);

		this.applyMove(idx, this.playerType);

		if (!this.online) {
			this.playerType = this.playerType === "X" ? "O" : "X";
		} else {
			try {
				await makeMove(session.gameId!, session.playerId!, posIndex.toPos(idx));
				this.playerTurn = false;
			} catch (err) {
				console.error(err);
			}
		}
	}
}

const board = new Board();

export type { Board };

export default board;
