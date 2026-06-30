// The sticky top bar beside the sidebar: a breadcrumb trail + project switcher on the left,
// signed-in email and Sign out on the right. Switching project navigates to its dashboard.
// When no project exists yet it falls back to the brand wordmark (create-first-project flow).
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { useAuth } from "../lib/auth";
import { useProjects } from "../lib/project";
import { Brand } from "./Brand";

// Section labels shown in the breadcrumb for each top-level route segment.
const SECTION_LABELS: Record<string, string> = {
  dashboard: "Dashboard",
  tournaments: "Tournaments",
  templates: "Templates",
  keys: "Keys",
};

export function TopBar() {
  const { user, logOut } = useAuth();
  const { projects, selected, select } = useProjects();
  const navigate = useNavigate();
  const location = useLocation();
  const { pid } = useParams<{ pid: string }>();

  // Switch the active project and deep-link to its dashboard.
  const onSwitch = (id: string) => {
    select(id);
    navigate(`/projects/${id}/dashboard`);
  };

  // The current section ("dashboard", "tournaments", …) from the path, for the breadcrumb.
  const section = location.pathname.split("/")[3] as keyof typeof SECTION_LABELS | undefined;
  const sectionLabel = section ? SECTION_LABELS[section] : undefined;

  return (
    <header className="tk-topbar">
      <div style={{ display: "flex", alignItems: "center", gap: 16, minWidth: 0 }}>
        {/* No project context → brand wordmark (first-run / create flow). */}
        {!pid && <Brand />}

        {/* Breadcrumb trail within the selected project. */}
        {pid && sectionLabel && (
          <nav className="tk-crumbs" aria-label="Breadcrumb">
            <span>{selected?.name ?? "Project"}</span>
            <span className="material-symbols-outlined" style={{ fontSize: 18 }} aria-hidden>chevron_right</span>
            <span className="current">{sectionLabel}</span>
          </nav>
        )}

        {/* Project switcher — only meaningful once a project exists. */}
        {projects.length > 0 && (
          <label style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <span className="tk-label">Project</span>
            <select
              className="tk-input"
              style={{ width: "auto", padding: "7px 10px", cursor: "pointer" }}
              value={selected?.id ?? ""}
              onChange={(e) => onSwitch(e.target.value)}
              aria-label="Select project"
            >
              {projects.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>

      <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
        <span style={{ color: "var(--tk-muted)", fontSize: 14 }}>{user?.email}</span>
        <button className="tk-btn tk-btn-ghost" onClick={() => logOut()}>
          Sign out
        </button>
      </div>
    </header>
  );
}
