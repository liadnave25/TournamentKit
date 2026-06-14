// Reads the current project id from the route (/projects/:pid/...). All project-scoped pages use it.
import { useParams } from "react-router-dom";

export function usePid(): string {
  const { pid } = useParams<{ pid: string }>();
  return pid as string;
}
