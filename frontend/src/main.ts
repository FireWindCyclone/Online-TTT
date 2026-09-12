import { createGame, joinGame } from "./apis";
import board from "./board";
import { session } from "./models";
import "./styles/style.css";
import showToast from "./toasts";
import { qs, setBusy } from "./utils";

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
	const gameId = (new FormData(joinForm).get("code") as string).toUpperCase();
	try {
		board.turn = false;
		session.gameId = gameId;
		session.sse = await joinGame(gameId);

		joinBtn.closest("div")!.hidden = true;

		joinPanel?.close();

		enterBtn.disabled = false;

		board.connected();

		showToast("Game joined");
	} catch (err) {
		console.error(err);
		joinStatus.textContent = "Failed to join game. Try again";
		enterBtn.disabled = false;
		board.turn = true;
		session.reset();
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
		session.gameId = gameId;
		session.sse = await joinGame();

		createStatus.textContent = "Share the below code or click to copy";
		createBtn.textContent = "Share Code";

		gameIdBtn.textContent = gameId;
		gameIdBtn.hidden = false;

		joinBtn.hidden = true;

		board.connected();

		showToast("Waiting for other player to join"); // NOTE: currently not dismissable above the dialog
		console.log(`Created game: ${session.gameId}`);
	} catch (err) {
		console.error(err);
		createStatus.textContent =
			"Failed to create game. Please try again after 1 min";
		gameIdBtn.hidden = true;
		board.turn = true;
		session.reset();
	} finally {
		setBusy(createPanel, false);
	}
});

export function exitGame() {
	session.sse!.close();
	session.reset();
	board.online = false;
	showToast("Exiting game");
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
