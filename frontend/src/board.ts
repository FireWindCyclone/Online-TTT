import { makeMove } from "./apis.ts";
import { exitGame, gameStatus, qs } from "./main.ts";
import type { CellType, SyncData } from "./models.ts";
import { posIndex } from "./models.ts";

class Board {
	private readonly cells: NodeListOf<HTMLButtonElement> =
		document.querySelectorAll<HTMLButtonElement>(".cell")!;
	private readonly playerTypeDisplay = qs<HTMLUListElement>(
		document,
		".player-type",
	);
	private readonly clearBtn: HTMLButtonElement = qs(document, ".reset-game");
	private readonly CELL_CLASS: Record<CellType, string> = {
		X: "cell-x",
		O: "cell-o",
	};
	private playerType: CellType = "X";
	private playerTurn: boolean = true;
	private _online: boolean = false;

	constructor() {
		this.cells.forEach((cell) => {
			cell.addEventListener("click", this);
		});
		this.clearBtn?.addEventListener("click", () => this.resetCells());
	}

	setMoveStatus() {
		if (!this._online) {
			return;
		}
		if (this.playerTurn) {
			gameStatus.textContent = "Your turn. Make a move";
		} else {
			gameStatus.textContent = "Opponent turn. Awaiting their move";
		}
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
			gameStatus.textContent = "You Win!";
		} else {
			gameStatus.textContent = "You Lose :(";
		}
	}

	set turn(turn: boolean) {
		this.playerTurn = turn;
	}

	set online(online: boolean) {
		this._online = online;
	}

	syncBoard(data: SyncData) {
		console.log("Syncing board");
		this.resetCells();
		for (const [i, c] of [...data.board].entries()) {
			if (c !== "*") {
				console.log(i, c);
				this.applyMove(i, c as CellType);
			}
		}
		this.playerType = data.playerType;
		this.playerTurn = data.playerTurn;

		if (this.playerType === "X") {
			return;
		}

		qs(this.playerTypeDisplay, ".player-type>li:first-child").textContent =
			"You (O)";
		qs(this.playerTypeDisplay, ".player-type>li:last-child").textContent =
			"Opponent (X)";
	}

	connected() {
		this.clearBtn.hidden = true;
		this.playerTypeDisplay.hidden = false;
	}

	disconnected() {
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

		this.applyMove(idx, this.playerType);
		if (this._online) {
			this.playerTurn = false;
			try {
				await makeMove(posIndex.toPos(idx));
				this.setMoveStatus();
			} catch (err) {
				console.error(err);
				exitGame();
			}
		} else {
			this.playerType = this.playerType === "X" ? "O" : "X";
		}
	}
}

const board = new Board();

export type { Board };

export default board;
