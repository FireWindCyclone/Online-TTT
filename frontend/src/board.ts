export type Session = {
	playerId: 0 | 1 | null;
	gameId: string | null;
};
export const session: Session = {
	playerId: null,
	gameId: null,
};

type CellType = "X" | "O";

const CELL_CLASS: Record<CellType, string> = {
	X: "cell-x",
	O: "cell-o",
};

class Board {
	private cells: NodeListOf<HTMLButtonElement>;
	private playerTurn: CellType = "X";
	private clearBtn: HTMLButtonElement;

	constructor() {
		this.cells = document.querySelectorAll<HTMLButtonElement>(".cell");
		this.cells.forEach((cell) => {
			cell.addEventListener("click", this);
		});
		this.clearBtn = document.querySelector(".reset-game")!;
		this.clearBtn?.addEventListener("click", (_) => this.reset());
	}
	applyMove(idx: number) {
		this.cells[idx].disabled = true;
		this.renderCell(idx);
		this.playerTurn = this.playerTurn === "X" ? "O" : "X";
	}
	renderCell(idx: number) {
		this.cells[idx].classList.add(CELL_CLASS[this.playerTurn]);
	}
	reset() {
		this.playerTurn = "X";
		this.cells.forEach((cell) => {
			cell.className = "cell";
			cell.disabled = false;
		});
	}

	connected() {
		this.reset();
		this.clearBtn.hidden = true;
		const playerTypeUl =
			document.querySelector<HTMLUListElement>(".player-type");
		playerTypeUl!.hidden = false;
	}

	handleEvent(event: Event) {
		const cell = event.currentTarget as HTMLButtonElement;
		const idx = parseInt(cell.dataset.index!, 10);
		this.applyMove(idx);
	}
}

const board = new Board();

export type { Board };

export default board;
