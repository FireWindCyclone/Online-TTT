import { createGame, joinGame } from "./apis.ts";
import board from "./board.ts";
import { session } from "./models.ts";
import "./styles/style.css";

export const gameStatus = document.querySelector<HTMLParagraphElement>(
	"header > .game-status",
);
const createPanel = document.querySelector<HTMLDialogElement>(".create-panel");
const joinPanel = document.querySelector<HTMLDialogElement>(".join-panel");
const joinForm = joinPanel?.querySelector("form");

const createStatus =
	createPanel?.querySelector<HTMLParagraphElement>(".game-status");
const joinStatus =
	joinPanel?.querySelector<HTMLParagraphElement>(".game-status");

const gameIdBtn = createPanel?.querySelector<HTMLButtonElement>(".game-id");

const createBtn = document.querySelector<HTMLButtonElement>(".create-game");
const joinBtn = document.querySelector<HTMLButtonElement>(".join-game");

document.querySelectorAll<HTMLButtonElement>(".close").forEach((btn) => {
	btn.addEventListener("click", () => btn.closest("dialog")?.close());
});

function setBusy(dialog: HTMLDialogElement, busy: boolean) {
	const closeBtn = dialog.querySelector<HTMLButtonElement>(".close");
	closeBtn!.disabled = busy;

	if (busy) {
		dialog.setAttribute("closedby", "none");
		dialog.setAttribute("aria-busy", "true");
	} else {
		dialog.removeAttribute("closedby");
		dialog.removeAttribute("aria-busy");
	}
}

gameIdBtn?.addEventListener("click", async (event) => {
	const gameIdBtn = event.currentTarget as HTMLButtonElement;
	try {
		navigator.clipboard.writeText(gameIdBtn.textContent);
	} catch (err) {
		console.error(`Failed to copy gameId: ${err}`);
	}
});

joinBtn?.addEventListener("click", () => {
	joinStatus!.textContent = "Enter game code to join";
	joinForm?.reset();
	joinPanel?.showModal();
});

joinForm?.addEventListener("submit", async (event) => {
	event.preventDefault();
	const enterBtn = event.target as HTMLButtonElement;
	enterBtn.disabled = true;

	setBusy(joinPanel!, true);
	const gameId = new FormData(joinForm).get("code") as string;
	try {
		board.setTurn(false);
		const sse = await joinGame(gameId, 1);
		session.playerId = 1;
		session.gameId = gameId;
		session.sse = sse;

		gameStatus!.textContent = "Game Joined";
		joinBtn!.closest("div")!.hidden = true;

		joinPanel?.close();

		enterBtn.disabled = false;

		board.connected();
	} catch (err) {
		console.error(err);
		joinStatus!.textContent = "Failed to join game. Try again";
		enterBtn.disabled = false;
		board.setTurn(true);
	} finally {
		setBusy(joinPanel!, false);
	}
});

createBtn?.addEventListener("click", async (event) => {
	const createBtn = event.currentTarget as HTMLButtonElement;
	createPanel?.showModal();
	if (session.gameId) {
		return;
	}
	setBusy(createPanel!, true);
	try {
		board.setTurn(false);
		const gameId = await createGame();
		const sse = await joinGame(gameId, 0);

		session.playerId = 0;
		session.gameId = gameId;
		session.sse = sse;

		createStatus!.textContent = "Share the below code or click to copy";
		gameStatus!.textContent = "Waiting for other player to join";
		createBtn.textContent = "Share Code";

		gameIdBtn!.textContent = gameId;
		gameIdBtn!.hidden = false;

		joinBtn!.hidden = true;

		board.connected();

		console.log(`Created game: ${session.gameId}`);
	} catch (err) {
		console.error(err);
		createStatus!.textContent = "Failed to create game :( Please try again";
		gameIdBtn!.hidden = true;
		board.setTurn(true);
	} finally {
		setBusy(createPanel!, false);
	}
});

export function exitGame() {
	session.sse!.close();
	session.playerId = null;
	session.gameId = null;
	session.sse = null;
	setTimeout(() => {
		gameStatus!.textContent =
			"Create or join a game to play with friends online";
		createStatus!.textContent = "Creating game...";
		joinStatus!.textContent = "Enter game code to join";
		joinForm?.reset();

		createBtn!.textContent = "Create";
		joinBtn!.textContent = "Join";

		gameIdBtn!.hidden = true;
		createBtn!.hidden = false;
		joinBtn!.hidden = false;
		joinBtn!.closest("div")!.hidden = false;
		board.disconnected();
	}, 5000);
}
