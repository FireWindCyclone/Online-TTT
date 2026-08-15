import { createGame } from "./api.ts";
import board, { session } from "./board.ts";
import "./styles/style.css";

const gameStatus = document.querySelector<HTMLParagraphElement>(
	"header > .game-status",
);
const createPanel = document.querySelector<HTMLDialogElement>(".create-panel");
const joinPanel = document.querySelector<HTMLDialogElement>(".join-panel");
const createStatus =
	createPanel?.querySelector<HTMLParagraphElement>(".game-status");
const gameIdBtn = document.querySelector<HTMLButtonElement>(".game-id");

const createBtn = document.querySelector<HTMLButtonElement>(".create-game");
const joinBtn = document.querySelector<HTMLButtonElement>(".join-game");

document.querySelectorAll<HTMLButtonElement>(".close").forEach((btn) => {
	btn.addEventListener("click", (_) => btn.closest("dialog")?.close());
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

joinBtn?.addEventListener("click", async (event) => {
	joinPanel?.showModal();
});

createBtn!.addEventListener("click", async (event) => {
	const createBtn = event.currentTarget as HTMLButtonElement;
	createPanel?.showModal();
	if (session.gameId) {
		return;
	}
	setBusy(createPanel!, true);
	try {
		const gameId = await createGame();
		session.playerId = 0;
		session.gameId = gameId;

		createStatus!.textContent = "Share the below code or click to copy";
		gameStatus!.textContent = "Waiting for other player to join";
		createBtn.textContent = "Share Code";

		gameIdBtn!.textContent = gameId;
		gameIdBtn!.hidden = false;

		joinBtn!.hidden = true;

		board.connected();

		console.log(`Created game: ${session}`);
	} catch (err) {
		console.error(`Failed to create game: ${err}`);
		createStatus!.textContent = "Failed to create game :( Please try again";
	} finally {
		setBusy(createPanel!, false);
	}
});
