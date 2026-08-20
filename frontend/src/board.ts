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

type CellType = "X" | "O";

export type SyncData = {
	board: string;
	playerType: CellType;
	playerTurn: boolean;
};

const CELL_CLASS: Record<CellType, string> = {
	X: "cell-x",
	O: "cell-o",
};

class Board {
	private cells: NodeListOf<HTMLButtonElement> =
		document.querySelectorAll<HTMLButtonElement>(".cell")!;
	private playerType: CellType = "X";
	private playerTurn: boolean = true;
	private clearBtn: HTMLButtonElement = document.querySelector(".reset-game")!;
	private playerTypeDisplay =
		document.querySelector<HTMLUListElement>(".player-type")!;

	constructor() {
		this.cells.forEach((cell) => {
			cell.addEventListener("click", this);
		});
		this.clearBtn?.addEventListener("click", (_) => this.reset());
	}
	applyMove(idx: number) {
		this.cells[idx].disabled = true;
		this.renderCell(idx);
		this.playerType = this.playerType === "X" ? "O" : "X";
	}
	renderCell(idx: number) {
		this.cells[idx].classList.add(CELL_CLASS[this.playerType]);
	}
	reset() {
		this.playerType = "X";
		this.cells.forEach((cell) => {
			cell.className = "cell";
			cell.disabled = false;
		});
	}

	syncBoard(data: SyncData) {
		this.playerType = data.playerType;
		this.playerTurn = data.playerTurn;
		for (const [i, c] of [...data.board].entries()) {
			if (c !== "*") {
				this.applyMove(i);
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
		this.reset();
		this.clearBtn.hidden = true;
		this.playerTypeDisplay.hidden = false;
	}

	handleEvent(event: Event) {
		if (!this.playerTurn) {
			return;
		}
		const cell = event.currentTarget as HTMLButtonElement;
		const idx = parseInt(cell.dataset.index!, 10);
		this.applyMove(idx);
	}
}

const board = new Board();

export type { Board };

export default board;
