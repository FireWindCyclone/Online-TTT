import { createGame, joinGame } from "./apis.ts";
import board from "./board.ts";
import { session } from "./models.ts";
import "./styles/style.css";

export function qs<T extends Element>(parent: ParentNode, sel: string): T {
	const el = parent.querySelector<T>(sel);
	if (!el) {
		throw new Error(`Missing element: ${sel}`);
	}
	return el;
}

export const gameStatus = qs<HTMLParagraphElement>(
	document,
	"header > .game-status",
);
const createPanel = qs<HTMLDialogElement>(document, ".create-panel");
const joinPanel = qs<HTMLDialogElement>(document, ".join-panel");
const joinForm = qs<HTMLFormElement>(joinPanel, "form");

const createStatus = qs<HTMLParagraphElement>(createPanel, ".game-status");
const joinStatus = qs<HTMLParagraphElement>(joinPanel, ".game-status");

const gameIdBtn = qs<HTMLButtonElement>(createPanel, ".game-id");

const createBtn = qs<HTMLButtonElement>(document, ".create-game");
const joinBtn = qs<HTMLButtonElement>(document, ".join-game");

document.querySelectorAll<HTMLButtonElement>(".close").forEach((btn) => {
	btn.addEventListener("click", () => btn.closest("dialog")?.close());
});

function setBusy(dialog: HTMLDialogElement, busy: boolean) {
	const closeBtn = qs<HTMLButtonElement>(dialog, ".close");
	closeBtn.disabled = busy;

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
		createStatus.textContent = "Copied!";
	} catch (err) {
		console.error(`Failed to copy gameId: ${err}`);
		createStatus.textContent = "Unable to copy";
	}
});

joinBtn?.addEventListener("click", () => {
	joinStatus.textContent = "Enter game code to join";
	joinForm?.reset();
	joinPanel?.showModal();
});

joinForm?.addEventListener("submit", async (event) => {
	event.preventDefault();
	const enterBtn = event.submitter as HTMLButtonElement;
	enterBtn.disabled = true;

	joinStatus.textContent = "Joining game...";

	setBusy(joinPanel, true);
	const gameId = new FormData(joinForm).get("code") as string;
	try {
		board.turn = false;
		const sse = await joinGame(gameId, 1);
		session.playerId = 1;
		session.gameId = gameId;
		session.sse = sse;

		joinBtn.closest("div")!.hidden = true;

		joinPanel?.close();

		enterBtn.disabled = false;

		board.connected();
	} catch (err) {
		console.error(err);
		joinStatus.textContent = "Failed to join game. Try again";
		enterBtn.disabled = false;
		board.turn = true;
	} finally {
		setBusy(joinPanel, false);
	}
});

createBtn?.addEventListener("click", async (event) => {
	const createBtn = event.currentTarget as HTMLButtonElement;
	createPanel?.showModal();

	createStatus.textContent = "Creating game...";
	if (session.gameId) {
		createStatus.textContent = "Share the below code or click to copy";
		return;
	}
	setBusy(createPanel, true);
	try {
		board.turn = false;
		const gameId = await createGame();
		const sse = await joinGame(gameId, 0);

		session.playerId = 0;
		session.gameId = gameId;
		session.sse = sse;

		createStatus.textContent = "Share the below code or click to copy";
		createBtn.textContent = "Share Code";

		gameIdBtn.textContent = gameId;
		gameIdBtn.hidden = false;

		joinBtn.hidden = true;

		board.connected();

		console.log(`Created game: ${session.gameId}`);
	} catch (err) {
		console.error(err);
		createStatus.textContent =
			"Failed to create game. Please try again after 1 min";
		gameIdBtn.hidden = true;
		board.turn = true;
	} finally {
		setBusy(createPanel, false);
	}
});

export function exitGame() {
	session.sse!.close();
	session.playerId = null;
	session.gameId = null;
	session.sse = null;
	board.online = false;
	setTimeout(() => {
		gameStatus.textContent =
			"Create or join a game to play with friends online";
		createStatus.textContent = "Creating game...";
		joinStatus.textContent = "Enter game code to join";
		joinForm?.reset();

		createBtn.textContent = "Create";
		joinBtn.textContent = "Join";

		gameIdBtn.hidden = true;
		createBtn.hidden = false;
		joinBtn.hidden = false;
		joinBtn.closest("div")!.hidden = false;
		board.disconnected();
	}, 5000);
}
