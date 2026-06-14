// The left navigation within a selected project: Dashboard, Tournaments, Templates, Keys, Analytics.
// Links are pid-scoped so deep links work and the active item is highlighted.
import { NavLink } from "react-router-dom";

const ITEMS = [
  { to: "dashboard", label: "Dashboard" },
  { to: "tournaments", label: "Tournaments" },
  { to: "templates", label: "Templates" },
  { to: "keys", label: "Keys" },
  { to: "analytics", label: "Analytics" },
];

// Renders the nav for project {pid}; each item links to /projects/{pid}/{section}.
export function ProjectNav({ pid }: { pid: string }) {
  return (
    <nav
      style={{
        display: "flex", flexDirection: "column", gap: 4,
        padding: 16, minWidth: 188, borderRight: "1px solid var(--tk-line)",
      }}
    >
      {ITEMS.map((item) => (
        <NavLink
          key={item.to}
          to={`/projects/${pid}/${item.to}`}
          style={({ isActive }) => ({
            display: "block",
            padding: "10px 12px",
            borderRadius: "var(--tk-radius-sm)",
            textDecoration: "none",
            fontFamily: "var(--tk-font-display)",
            fontWeight: 700,
            fontSize: 14,
            color: isActive ? "var(--tk-on-primary)" : "var(--tk-on-surface)",
            background: isActive ? "var(--tk-primary)" : "transparent",
          })}
        >
          {item.label}
        </NavLink>
      ))}
    </nav>
  );
}
