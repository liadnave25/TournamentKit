// Create-a-project screen. Shown automatically when the developer has no projects yet, and reachable
// to add more. On success it reveals the first API key ONCE, then refreshes the project list.
import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { api, ApiError } from "../lib/api";
import { useProjects } from "../lib/project";
import { ApiKeyReveal } from "../components/ApiKeyReveal";

export function CreateProjectPage({ firstRun = false }: { firstRun?: boolean }) {
  const { reload, select } = useProjects();
  const navigate = useNavigate();
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<{ id: string; apiKey: string } | null>(null);

  // Calls POST /portal/projects and switches into the show-key-once view on success.
  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const res = await api.createProject(name.trim());
      setCreated({ id: res.id, apiKey: res.apiKey });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not create the project.");
    } finally {
      setBusy(false);
    }
  };

  // After the developer saves the key: refresh the list, select the new project, go to its dashboard.
  const finish = async () => {
    await reload();
    if (created) {
      select(created.id);
      navigate(`/projects/${created.id}/dashboard`);
    } else {
      navigate("/");
    }
  };

  return (
    <div style={{ maxWidth: 560, margin: "0 auto", padding: "48px 20px" }}>
      <h1 className="tk-display" style={{ fontSize: 30, marginBottom: 6 }}>
        {firstRun ? "Create your first project" : "New project"}
      </h1>
      <p style={{ color: "var(--tk-muted)", marginTop: 0, marginBottom: 26 }}>
        A project groups your tournaments, templates and API key. You can create more later.
      </p>

      {created ? (
        // Show-key-once step.
        <ApiKeyReveal apiKey={created.apiKey} onDone={finish} />
      ) : (
        <form onSubmit={onSubmit} className="tk-card" style={{ padding: 22 }}>
          <label className="tk-label" htmlFor="pname">Project name</label>
          <input
            id="pname" required className="tk-input" style={{ marginTop: 6, marginBottom: 18 }}
            value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. FifaNight"
            autoFocus
          />
          {error && (
            <div role="alert" style={{ color: "var(--tk-danger)", fontSize: 14, marginBottom: 14 }}>
              {error}
            </div>
          )}
          <button
            type="submit" className="tk-btn tk-btn-primary"
            disabled={busy || name.trim().length === 0}
          >
            {busy ? "Creating…" : "Create project"}
          </button>
        </form>
      )}
    </div>
  );
}
