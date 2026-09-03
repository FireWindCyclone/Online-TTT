import type { Pos } from "./models";

export const posIndex = {
	MAX_COLS: 3 as const,
	toIndex(pos: Pos): number {
		return pos.row * this.MAX_COLS + pos.col;
	},
	toPos(idx: number): Pos {
		return { row: Math.floor(idx / this.MAX_COLS), col: idx % this.MAX_COLS };
	},
};

export function qs<T extends Element>(parent: ParentNode, sel: string): T {
	const el = parent.querySelector<T>(sel);
	if (!el) {
		throw new Error(`Missing element: ${sel}`);
	}
	return el;
}

export function setBusy(dialog: HTMLDialogElement, busy: boolean) {
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
