type CellType = "X" | "O";

const CELL_CLASS: Record<CellType, string> = {
	X: "cell-x",
	O: "cell-o",
};

export default class Board {
	private board: (CellType | null)[];
	private playerTurn: CellType;
	private cells: NodeListOf<HTMLButtonElement>;

	constructor() {
		this.playerTurn = "X";
		this.board = Array(9).fill(null);
		this.cells = document.querySelectorAll<HTMLButtonElement>(".cell");
		this.cells.forEach((cell) => {
			cell.addEventListener("click", this);
		});
		document
			.querySelector(".reset-game")
			?.addEventListener("click", (_) => this.reset());
	}
	applyMove(idx: number) {
		this.board[idx] = this.playerTurn;
		this.cells[idx].disabled = true;
		this.renderCell(idx);
		this.playerTurn = this.playerTurn === "X" ? "O" : "X";
	}
	renderCell(idx: number) {
		this.cells[idx].classList.add(CELL_CLASS[this.playerTurn]);
	}
	reset() {
		this.board = Array(9).fill(null);
		this.playerTurn = "X";
		this.cells.forEach((cell) => {
			cell.className = "cell";
			cell.disabled = false;
		});
	}

	handleEvent(event: Event) {
		const cell = event.currentTarget as HTMLButtonElement;
		const idx = parseInt(cell.dataset.index!, 10);
		this.applyMove(idx);
	}
}
