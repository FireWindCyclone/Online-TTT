import confetti from "@hiseb/confetti";
import { joinGame, makeMove } from "./apis";
import { gameStatus } from "./main";
import { type CellType, type SyncData, session } from "./models";
import showToast from "./toasts";
import { posIndex, qs } from "./utils";

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
		if (!this._online) return;

		if (this.playerTurn) {
			gameStatus.textContent = "Your turn. Make a move";
		} else {
			gameStatus.textContent = "Opponent turn. Awaiting their move";
		}
	}

	applyMove(idx: number, moveType: CellType) {
		this.cells[idx].classList.add(this.CELL_CLASS[moveType], "filled");
		this.cells[idx].setAttribute("aria-disabled", "true");
	}

	private resetCells() {
		this.cells.forEach(this.resetCell);
	}

	private resetCell(cell: HTMLButtonElement) {
		cell.className = "cell";
		cell.removeAttribute("aria-disabled");
	}

	win(winType: CellType, winCells: number[]) {
		for (const idx of winCells) {
			this.cells[idx].classList.add("win");
		}
		if (winType === this.playerType) {
			gameStatus.textContent = "You Win!";
			confetti({ size: 2 });
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

		this.playerTypeDisplay.firstElementChild!.textContent =
			`You (${data.playerType})`;
		this.playerTypeDisplay.lastElementChild!.textContent =
			`Opponent (${data.playerType === "X" ? "O" : "X"})`;
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
		const cell = event.currentTarget as HTMLButtonElement;
		if (cell.getAttribute("aria-disabled") === "true") return;
		if (!this.playerTurn) {
			showToast("Not your turn");
			return;
		}
		const idx = parseInt(cell.dataset.index!, 10);

		this.applyMove(idx, this.playerType);
		if (this._online) {
			this.playerTurn = false;
			try {
				await makeMove(posIndex.toPos(idx));
				this.setMoveStatus();
			} catch (err) {
				console.error(err);
				session.sse = await joinGame();
				showToast("Something went wrong. Try again");
				this.resetCell(this.cells[idx]);
			}
		} else {
			this.playerType = this.playerType === "X" ? "O" : "X";
		}
	}
}

const board = new Board();

export type { Board };

export default board;
