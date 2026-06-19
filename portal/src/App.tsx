// The app shell: an auth guard routing to AuthPage, create-first-project, or pid-scoped routes for deep links.
import { useEffect } from "react";
import { Navigate, Outlet, Route, Routes, useParams } from "react-router-dom";
import { useAuth } from "./lib/auth";
import { ProjectProvider, useProjects } from "./lib/project";
import { TopBar } from "./components/TopBar";
import { ProjectNav } from "./components/ProjectNav";
import { Loading, ErrorState } from "./components/StateBlock";
import { AuthPage } from "./pages/AuthPage";
import { CreateProjectPage } from "./pages/CreateProjectPage";
import { DashboardPage } from "./pages/DashboardPage";
import { TemplatesPage } from "./pages/TemplatesPage";
import { TournamentsPage } from "./pages/TournamentsPage";
import { TournamentDetailPage } from "./pages/TournamentDetailPage";
import { KeysPage } from "./pages/KeysPage";

export function App() {
  const { user, loading } = useAuth();

  // Wait for Firebase to report whether someone is signed in before deciding what to show.
  if (loading) return <Loading label="Starting…" />;

  // No session → the login / sign-up screen.
  if (!user) return <AuthPage />;

  // Signed in → load the developer's projects, then route within the app.
  return (
    <ProjectProvider>
      <SignedInApp />
    </ProjectProvider>
  );
}

// The signed-in experience: resolves project state then routes (pid-scoped for deep links).
function SignedInApp() {
  const { projects, selected, loading, error, reload } = useProjects();

  if (loading) return <Loading label="Loading your projects…" />;
  if (error) {
    return (
      <div style={{ maxWidth: 560, margin: "60px auto", padding: 20 }}>
        <ErrorState message={error.message} onRetry={reload} />
      </div>
    );
  }

  // Brand-new developer with no projects → prompt to create the first one.
  if (projects.length === 0) {
    return (
      <>
        <TopBar />
        <CreateProjectPage firstRun />
      </>
    );
  }

  const home = `/projects/${selected?.id ?? projects[0].id}/dashboard`;

  return (
    <>
      <TopBar />
      <Routes>
        {/* Standalone create-project screen (reachable from anywhere). */}
        <Route path="/projects/new" element={<CreateProjectPage />} />

        {/* Everything else is scoped to a project. */}
        <Route path="/projects/:pid" element={<ProjectLayout />}>
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="templates" element={<TemplatesPage />} />
          <Route path="tournaments" element={<TournamentsPage />} />
          <Route path="tournaments/:tid" element={<TournamentDetailPage />} />
          <Route path="keys" element={<KeysPage />} />
        </Route>

        {/* Default + unknown → the selected project's dashboard. */}
        <Route path="*" element={<Navigate to={home} replace />} />
      </Routes>
    </>
  );
}

// Renders the nav + routed page for a project, keeping the URL pid in sync with the selection and guarding unknown/unowned pids.
function ProjectLayout() {
  const { pid } = useParams<{ pid: string }>();
  const { projects, selected, select } = useProjects();

  // Mirror the URL's pid into the selected project (covers deep links + browser navigation).
  useEffect(() => {
    if (pid && pid !== selected?.id && projects.some((p) => p.id === pid)) {
      select(pid);
    }
  }, [pid, selected?.id, projects, select]);

  // A pid the developer doesn't own (or a stale link) → bounce to their first project.
  if (pid && !projects.some((p) => p.id === pid)) {
    return <Navigate to={`/projects/${projects[0].id}/dashboard`} replace />;
  }

  return (
    <div style={{ display: "flex", alignItems: "flex-start" }}>
      <ProjectNav pid={pid as string} />
      <main style={{ flex: 1, minWidth: 0 }}>
        <Outlet />
      </main>
    </div>
  );
}
