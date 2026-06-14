// A simple modal dialog: a dimmed backdrop + a centered Floodlight card. Closes on backdrop click
// or Escape. Used for delete confirmation and the result-override form.
import { useEffect, type ReactNode } from "react";

export function Dialog({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  // Close on Escape for keyboard users.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div
      onClick={onClose}
      style={{
        position: "fixed", inset: 0, zIndex: 100,
        background: "rgba(6,8,11,0.66)",
        display: "grid", placeItems: "center", padding: 20,
      }}
      role="dialog"
      aria-modal="true"
      aria-label={title}
    >
      <div
        className="tk-card"
        onClick={(e) => e.stopPropagation()}
        style={{ padding: 22, width: "100%", maxWidth: 460 }}
      >
        <h2 className="tk-display" style={{ fontSize: 20, margin: "0 0 14px" }}>{title}</h2>
        {children}
      </div>
    </div>
  );
}
