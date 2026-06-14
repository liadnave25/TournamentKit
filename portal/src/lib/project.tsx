// Tracks the developer's owned projects and which one is currently selected. The selected projectId
// scopes every per-project call (analytics, templates, …). Loaded after sign-in.
import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { api, ApiError } from "./api";
import { useAuth } from "./auth";
import type { ProjectSummary } from "./types";

interface ProjectContextValue {
  projects: ProjectSummary[];
  selected: ProjectSummary | null;
  loading: boolean;
  error: ApiError | null;
  select: (id: string) => void;
  reload: () => Promise<void>;
}

const ProjectContext = createContext<ProjectContextValue | null>(null);

// Loads the signed-in developer's projects and remembers the selected one.
export function ProjectProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const [projects, setProjects] = useState<ProjectSummary[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  // Fetches the project list; keeps a sensible selection.
  const reload = async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await api.listProjects();
      setProjects(list);
      setSelectedId((prev) => (prev && list.some((p) => p.id === prev) ? prev : list[0]?.id ?? null));
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError("TK_UNKNOWN", String(e), 0));
    } finally {
      setLoading(false);
    }
  };

  // (Re)load whenever the signed-in user changes.
  useEffect(() => {
    if (user) reload();
    else {
      setProjects([]);
      setSelectedId(null);
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]);

  const selected = useMemo(
    () => projects.find((p) => p.id === selectedId) ?? null,
    [projects, selectedId]
  );

  return (
    <ProjectContext.Provider
      value={{ projects, selected, loading, error, select: setSelectedId, reload }}
    >
      {children}
    </ProjectContext.Provider>
  );
}

// Hook to read the project context; throws if used outside ProjectProvider.
export function useProjects(): ProjectContextValue {
  const ctx = useContext(ProjectContext);
  if (!ctx) throw new Error("useProjects must be used within ProjectProvider");
  return ctx;
}
