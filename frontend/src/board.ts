import { makeMove } from "./apis.ts";
import { gameStatus } from "./main.ts";
import type { CellType, SyncData } from "./models.ts";
import { posIndex } from "./models.ts";

class Board {
	private readonly cells: NodeListOf<HTMLButtonElement> =
		document.querySelectorAll<HTMLButtonElement>(".cell")!;
	private readonly playerTypeDisplay =
		document.querySelector<HTMLUListElement>(".player-type")!;
	private readonly clearBtn: HTMLButtonElement =
		document.querySelector(".reset-game")!;
	private readonly CELL_CLASS: Record<CellType, string> = {
		X: "cell-x",
		O: "cell-o",
	};
	private playerType: CellType = "X";
	private playerTurn: boolean = true;
	private online: boolean = false;

	constructor() {
		this.cells.forEach((cell) => {
			cell.addEventListener("click", this);
		});
		this.clearBtn?.addEventListener("click", () => this.resetCells());
	}

	applyMove(idx: number, moveType: CellType) {
		this.cells[idx].classList.add(this.CELL_CLASS[moveType]);
		this.cells[idx].disabled = true;
	}

	resetCells() {
		this.cells.forEach((cell) => {
			cell.className = "cell";
			cell.disabled = false;
		});
	}
	win(winType: CellType, winCells: number[]) {
		for (const idx of winCells) {
			this.cells[idx].classList.add("win");
		}
		if (winType === this.playerType) {
			gameStatus!.textContent = "You Win!";
		} else {
			gameStatus!.textContent = "You Lose :(";
		}
	}

	setTurn(turn: boolean) {
		this.playerTurn = turn;
	}

	syncBoard(data: SyncData) {
		console.log("Syncing board");
		this.playerType = data.playerType;
		this.playerTurn = data.playerTurn;
		for (const [i, c] of [...data.board].entries()) {
			if (c !== "*") {
				console.log(i, c);
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
		this.online = true;
		this.clearBtn.hidden = true;
		this.playerTypeDisplay.hidden = false;
	}

	disconnected() {
		this.online = false;
		this.clearBtn.hidden = false;
		this.playerTypeDisplay.hidden = true;
		this.playerTurn = true;
	}

	async handleEvent(event: Event) {
		if (!this.playerTurn) {
			return;
		}
		const cell = event.currentTarget as HTMLButtonElement;
		const idx = parseInt(cell.dataset.index!, 10);

		try {
			if (this.online) {
				await makeMove(posIndex.toPos(idx));
				this.playerTurn = false;
			}
			this.applyMove(idx, this.playerType);
		} catch (err) {
			console.error(err);
		}

		if (!this.online) {
			this.playerType = this.playerType === "X" ? "O" : "X";
		}
	}
}

const board = new Board();

export type { Board };

export default board;
