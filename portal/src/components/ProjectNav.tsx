// The fixed dark sidebar within a selected project: brand, primary nav (Dashboard, Tournaments,
// Templates, Keys), and bottom utility links. Links are pid-scoped so deep links work and the
// active item is highlighted. Mirrors the Stitch "Project Console" sidebar.
import { NavLink } from "react-router-dom";

const ITEMS = [
  { to: "dashboard", label: "Dashboard", icon: "dashboard" },
  { to: "tournaments", label: "Tournaments", icon: "emoji_events" },
  { to: "templates", label: "Templates", icon: "description" },
  { to: "keys", label: "Keys", icon: "vpn_key" },
];

// Renders the sidebar nav for project {pid}; each item links to /projects/{pid}/{section}.
export function ProjectNav({ pid }: { pid: string }) {
  return (
    <nav className="tk-sidebar" aria-label="Project navigation">
      {/* Brand block. */}
      <div className="tk-sidebar-brandrow">
        <span className="tk-sidebar-logo">
          <span className="material-symbols-outlined" aria-hidden>sports_esports</span>
        </span>
        <div>
          <div className="tk-display" style={{ fontSize: 18, color: "var(--tk-sidebar-on)" }}>Project Console</div>
          <div className="tk-label" style={{ fontSize: 10, color: "var(--tk-sidebar-muted)" }}>Developer Environment</div>
        </div>
      </div>

      {/* Primary navigation. */}
      <div style={{ display: "flex", flexDirection: "column", gap: 6, padding: "16px 12px", flex: 1, overflowY: "auto" }}>
        {ITEMS.map((item) => (
          <NavLink
            key={item.to}
            to={`/projects/${pid}/${item.to}`}
            className={({ isActive }) => `tk-nav-item${isActive ? " active" : ""}`}
          >
            <span className="material-symbols-outlined" aria-hidden>{item.icon}</span>
            {item.label}
          </NavLink>
        ))}
      </div>

      {/* Bottom utility links (decorative, matching the design). */}
      <div style={{ display: "flex", flexDirection: "column", gap: 6, padding: "16px 12px", borderTop: "1px solid rgba(255,255,255,0.12)" }}>
        <a
          className="tk-nav-item"
          href="https://github.com/liadnave25/TournamentKit/blob/main/sdk-api.md"
          target="_blank"
          rel="noreferrer"
        >
          <span className="material-symbols-outlined" aria-hidden>menu_book</span>
          Documentation
        </a>
      </div>
    </nav>
  );
}
