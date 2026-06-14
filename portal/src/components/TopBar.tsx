// The persistent top bar: brand, project switcher (when the developer owns multiple), and the
// signed-in email with sign-out. Switching project navigates to that project's dashboard.
import { useNavigate } from "react-router-dom";
import { useAuth } from "../lib/auth";
import { useProjects } from "../lib/project";
import { Brand } from "./Brand";

export function TopBar() {
  const { user, logOut } = useAuth();
  const { projects, selected, select } = useProjects();
  const navigate = useNavigate();

  // Switch the active project and deep-link to its dashboard.
  const onSwitch = (id: string) => {
    select(id);
    navigate(`/projects/${id}/dashboard`);
  };

  return (
    <header
      style={{
        display: "flex", alignItems: "center", gap: 16,
        padding: "14px 22px",
        borderBottom: "1px solid var(--tk-line)",
        background: "rgba(22,27,35,0.7)",
        backdropFilter: "blur(8px)",
        position: "sticky", top: 0, zIndex: 10,
      }}
    >
      <Brand />

      {/* Project switcher — only meaningful once a project exists. */}
      {projects.length > 0 && (
        <label style={{ display: "flex", alignItems: "center", gap: 8, marginLeft: 8 }}>
          <span className="tk-label">Project</span>
          <select
            className="tk-input"
            style={{ width: "auto", padding: "8px 10px", cursor: "pointer" }}
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

      <div style={{ flex: 1 }} />

      <span style={{ color: "var(--tk-muted)", fontSize: 14 }}>{user?.email}</span>
      <button className="tk-btn tk-btn-ghost" onClick={() => logOut()}>
        Sign out
      </button>
    </header>
  );
}
