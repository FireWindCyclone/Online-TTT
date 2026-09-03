import { qs } from "./utils";

const toasts = qs<HTMLElement>(document, ".toasts");

export default function showToast(message: string, duration: number = 3000) {
	if (toasts.matches(":popover-open")) toasts.hidePopover();
	toasts.showPopover();

	if (toasts.children.length >= 3) toasts.firstElementChild?.remove();

	const el = document.createElement("div");
	el.classList.add("toast");
	el.textContent = message;
	toasts.append(el);

	setTimeout(() => dismissToast(el), duration);
}

function dismissToast(toast: HTMLElement | null) {
	if (!toast || toast.classList.contains("leaving")) return;
	toast.classList.add("leaving");
	toast.addEventListener("animationend", () => toast.remove(), { once: true });
}

toasts.addEventListener("click", (event) =>
	dismissToast((event.target as HTMLElement).closest(".toast")),
);
