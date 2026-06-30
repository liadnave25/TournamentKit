// Templates CRUD with a create/edit form mirroring the server's validation and surfacing its TKError messages verbatim.
import { useState, type FormEvent } from "react";
import { api, ApiError, type TemplateInput } from "../lib/api";
import { usePid } from "../lib/usePid";
import { useAsync } from "../lib/useAsync";
import { Loading, Empty, ErrorState } from "../components/StateBlock";
import { Dialog } from "../components/Dialog";
import type { Template, TemplateType } from "../lib/types";

const TYPES: TemplateType[] = ["KNOCKOUT", "LEAGUE", "GROUPS_KNOCKOUT", "TALLY"];

export function TemplatesPage() {
  const pid = usePid();
  const { data, loading, error, reload } = useAsync<Template[]>(() => api.listTemplates(pid), [pid]);
  const [editing, setEditing] = useState<Template | "new" | null>(null);
  const [deleting, setDeleting] = useState<Template | null>(null);

  return (
    <div style={{ maxWidth: 980, padding: 24 }}>
      <div style={{ display: "flex", alignItems: "center", marginBottom: 18 }}>
        <h1 className="tk-display" style={{ fontSize: 24, margin: 0 }}>Tournament Templates</h1>
        <div style={{ flex: 1 }} />
        <button className="tk-btn tk-btn-primary" onClick={() => setEditing("new")}>New template</button>
      </div>
      <p style={{ color: "var(--tk-muted)", marginTop: 0, marginBottom: 22, fontSize: 14 }}>
        Templates define a tournament's rules. Editing a template never changes tournaments already
        running — their rules are snapshotted when they start.
      </p>

      {loading && <Loading label="Loading templates…" />}
      {error && <ErrorState message={error.message} onRetry={reload} />}
      {data && data.length === 0 && <Empty label="No templates yet — create one to get started." />}

      {data && data.length > 0 && (
        <div style={{ display: "grid", gap: 12 }}>
          {data.map((t) => (
            <TemplateRow key={t.id} t={t} onEdit={() => setEditing(t)} onDelete={() => setDeleting(t)} />
          ))}
        </div>
      )}

      {editing && (
        <TemplateForm
          pid={pid}
          existing={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => { setEditing(null); reload(); }}
        />
      )}
      {deleting && (
        <DeleteDialog
          pid={pid}
          template={deleting}
          onClose={() => setDeleting(null)}
          onDeleted={() => { setDeleting(null); reload(); }}
        />
      )}
    </div>
  );
}

// One template summary row with edit/delete actions.
function TemplateRow({ t, onEdit, onDelete }: { t: Template; onEdit: () => void; onDelete: () => void }) {
  return (
    <div className="tk-card" style={{ padding: 16, display: "flex", alignItems: "center", gap: 16 }}>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <span className="tk-display" style={{ fontSize: 16 }}>{t.type}</span>
          <span className="tk-num" style={{ color: "var(--tk-muted)", fontSize: 12 }}>{t.id}</span>
        </div>
        <div style={{ color: "var(--tk-muted)", fontSize: 13, marginTop: 4 }}>
          {t.type === "TALLY"
            ? "Points leaderboard (PTS)"
            : `Win ${t.scoring.win} · Draw ${t.scoring.draw} · Loss ${t.scoring.loss} · Max ${t.maxParticipants}`}
        </div>
      </div>
      <button className="tk-btn tk-btn-ghost" onClick={onEdit}>Edit</button>
      <button className="tk-btn tk-btn-ghost" style={{ color: "var(--tk-danger)" }} onClick={onDelete}>
        Delete
      </button>
    </div>
  );
}

// The create/edit form, with inline validation that mirrors the server's rules.
function TemplateForm({
  pid, existing, onClose, onSaved,
}: { pid: string; existing: Template | null; onClose: () => void; onSaved: () => void }) {
  const [type, setType] = useState<TemplateType>(existing?.type ?? "KNOCKOUT");
  const [win, setWin] = useState(existing?.scoring.win ?? 3);
  const [draw, setDraw] = useState(existing?.scoring.draw ?? 1);
  const [loss, setLoss] = useState(existing?.scoring.loss ?? 0);
  const [maxParticipants, setMax] = useState(existing?.maxParticipants ?? 8);
  const [serverError, setServerError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // TALLY ignores scoring/maxParticipants at runtime, but the server still validates them.
  const isTally = type === "TALLY";

  // Changes the type; switching to TALLY drops scoring/max to valid no-op defaults so the form submits cleanly.
  const onTypeChange = (next: TemplateType) => {
    setType(next);
    if (next === "TALLY") {
      setWin(0); setDraw(0); setLoss(0); setMax(2);
    }
  };

  // Client-side validation matching the server (scoring ≥ 0, max ≥ 2).
  const fieldErrors: Record<string, string> = {};
  if (win < 0) fieldErrors.win = "must be ≥ 0";
  if (draw < 0) fieldErrors.draw = "must be ≥ 0";
  if (loss < 0) fieldErrors.loss = "must be ≥ 0";
  if (maxParticipants < 2) fieldErrors.maxParticipants = "must be ≥ 2";
  const valid = Object.keys(fieldErrors).length === 0;

  // Submits create or update; the server is the source of truth, so its TKError is shown too.
  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!valid) return;
    setServerError(null);
    setBusy(true);
    const body: TemplateInput = {
      type,
      scoring: { win, draw, loss },
      maxParticipants,
    };
    try {
      if (existing) await api.updateTemplate(pid, existing.id, body);
      else await api.createTemplate(pid, body);
      onSaved();
    } catch (err) {
      setServerError(err instanceof ApiError ? err.message : "Could not save the template.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog title={existing ? "Edit template" : "New template"} onClose={onClose}>
      <form onSubmit={onSubmit}>
        <Field label="Type">
          <select className="tk-input" value={type} onChange={(e) => onTypeChange(e.target.value as TemplateType)}>
            {TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
        </Field>

        {isTally ? (
          // TALLY is an open-ended points leaderboard: only PTS matters, so scoring and max are hidden
          // (the server still requires valid values, which onTypeChange has set to no-op defaults).
          <div style={{ color: "var(--tk-muted)", fontSize: 13, marginTop: -4, marginBottom: 12 }}>
            TALLY is an open-ended points leaderboard — players accumulate points (PTS) via the SDK.
            It has no win/draw/loss scoring and no participant cap.
          </div>
        ) : (
          <>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 12 }}>
              <NumberField label="Win" value={win} onChange={setWin} error={fieldErrors.win} />
              <NumberField label="Draw" value={draw} onChange={setDraw} error={fieldErrors.draw} />
              <NumberField label="Loss" value={loss} onChange={setLoss} error={fieldErrors.loss} />
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr", gap: 12 }}>
              <NumberField label="Max participants" value={maxParticipants} onChange={setMax} error={fieldErrors.maxParticipants} />
            </div>
          </>
        )}

        {serverError && (
          <div role="alert" style={{ color: "var(--tk-danger)", fontSize: 14, marginBottom: 12 }}>{serverError}</div>
        )}

        <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
          <button type="button" className="tk-btn tk-btn-ghost" onClick={onClose}>Cancel</button>
          <button type="submit" className="tk-btn tk-btn-primary" disabled={!valid || busy}>
            {busy ? "Saving…" : existing ? "Save changes" : "Create"}
          </button>
        </div>
      </form>
    </Dialog>
  );
}

// The delete confirmation; surfaces the server's 409 (template in use) clearly.
function DeleteDialog({
  pid, template, onClose, onDeleted,
}: { pid: string; template: Template; onClose: () => void; onDeleted: () => void }) {
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Calls DELETE; a 409 (a non-finished tournament references it) is shown, not hidden.
  const confirm = async () => {
    setError(null);
    setBusy(true);
    try {
      await api.deleteTemplate(pid, template.id);
      onDeleted();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete the template.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog title="Delete template?" onClose={onClose}>
      <p style={{ color: "var(--tk-muted)", marginTop: 0 }}>
        Delete <strong style={{ color: "var(--tk-on-surface)" }}>{template.type}</strong> ({template.id})?
        This can't be undone.
      </p>
      {error && <div role="alert" style={{ color: "var(--tk-danger)", fontSize: 14, marginBottom: 12 }}>{error}</div>}
      <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
        <button className="tk-btn tk-btn-ghost" onClick={onClose}>Cancel</button>
        <button className="tk-btn" style={{ background: "var(--tk-danger)", color: "#fff", borderColor: "transparent" }} onClick={confirm} disabled={busy}>
          {busy ? "Deleting…" : "Delete"}
        </button>
      </div>
    </Dialog>
  );
}

// A labeled field wrapper.
function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 12 }}>
      <div className="tk-label" style={{ marginBottom: 6 }}>{label}</div>
      {children}
    </div>
  );
}

// A labeled numeric input with an inline error.
function NumberField({
  label, value, onChange, error,
}: { label: string; value: number; onChange: (n: number) => void; error?: string }) {
  return (
    <div style={{ marginBottom: 4 }}>
      <div className="tk-label" style={{ marginBottom: 6 }}>{label}</div>
      <input
        className="tk-input" type="number" value={value}
        onChange={(e) => onChange(e.target.value === "" ? 0 : Number(e.target.value))}
        style={error ? { borderColor: "var(--tk-danger)" } : undefined}
      />
      {error && <div style={{ color: "var(--tk-danger)", fontSize: 12, marginTop: 4 }}>{error}</div>}
    </div>
  );
}
