// Login + Sign-up on one toggleable screen via Firebase Email/Password, after which the router sends the developer into the app.
import { useState, type FormEvent } from "react";
import { FirebaseError } from "firebase/app";
import { useAuth } from "../lib/auth";
import { Brand } from "../components/Brand";

export function AuthPage() {
  const { signIn, signUp } = useAuth();
  const [mode, setMode] = useState<"signIn" | "signUp">("signIn");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Submits the email/password form for the current mode and surfaces a readable error.
  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      if (mode === "signIn") await signIn(email, password);
      else await signUp(email, password);
    } catch (err) {
      setError(friendlyAuthError(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={{ minHeight: "100vh", display: "grid", placeItems: "center", padding: 20 }}>
      <div style={{ width: "100%", maxWidth: 380 }}>
        <div style={{ marginBottom: 26 }}>
          <Brand size={26} />
        </div>

        <h1 className="tk-display" style={{ fontSize: 30, margin: "0 0 6px" }}>
          {mode === "signIn" ? "Welcome back" : "Create your account"}
        </h1>
        <p style={{ color: "var(--tk-muted)", marginTop: 0, marginBottom: 24 }}>
          The management console for your TournamentKit projects.
        </p>

        <form onSubmit={onSubmit} className="tk-card" style={{ padding: 22 }}>
          <label className="tk-label" htmlFor="email">Email</label>
          <input
            id="email" type="email" required autoComplete="email"
            className="tk-input" style={{ marginTop: 6, marginBottom: 16 }}
            value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@studio.com"
          />

          <label className="tk-label" htmlFor="password">Password</label>
          <input
            id="password" type="password" required minLength={6}
            autoComplete={mode === "signIn" ? "current-password" : "new-password"}
            className="tk-input" style={{ marginTop: 6, marginBottom: 18 }}
            value={password} onChange={(e) => setPassword(e.target.value)} placeholder="••••••••"
          />

          {error && (
            <div role="alert" style={{ color: "var(--tk-danger)", fontSize: 14, marginBottom: 14 }}>
              {error}
            </div>
          )}

          <button type="submit" className="tk-btn tk-btn-primary" style={{ width: "100%" }} disabled={busy}>
            {busy ? "Please wait…" : mode === "signIn" ? "Sign in" : "Sign up"}
          </button>
        </form>

        <button
          className="tk-btn tk-btn-ghost"
          style={{ width: "100%", marginTop: 12, border: "none" }}
          onClick={() => { setMode(mode === "signIn" ? "signUp" : "signIn"); setError(null); }}
        >
          {mode === "signIn" ? "Need an account? Sign up" : "Already have an account? Sign in"}
        </button>
      </div>
    </div>
  );
}

// Maps Firebase auth error codes to short, human messages.
function friendlyAuthError(err: unknown): string {
  if (err instanceof FirebaseError) {
    switch (err.code) {
      case "auth/invalid-credential":
      case "auth/wrong-password":
      case "auth/user-not-found":
        return "Wrong email or password.";
      case "auth/email-already-in-use":
        return "That email is already registered — try signing in.";
      case "auth/weak-password":
        return "Password should be at least 6 characters.";
      case "auth/invalid-email":
        return "That doesn't look like a valid email.";
      default:
        return err.message;
    }
  }
  return "Something went wrong. Please try again.";
}
