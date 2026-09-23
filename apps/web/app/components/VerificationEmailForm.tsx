"use client";

import { useEffect, useState, type FormEvent } from "react";
import { api, ApiError } from "../lib/api";

export default function VerificationEmailForm({ expanded = false }: { expanded?: boolean }) {
  const [busy, setBusy] = useState(false);
  const [remaining, setRemaining] = useState(0);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    if (remaining <= 0) return;
    const timer = window.setTimeout(() => setRemaining(value => Math.max(0, value - 1)), 1000);
    return () => window.clearTimeout(timer);
  }, [remaining]);

  async function resend(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || remaining > 0) return;
    const email = new FormData(event.currentTarget).get("email");
    setBusy(true);
    setMessage("");
    setError("");
    try {
      await api("/auth/verification-email", "POST", { email });
      setMessage("Se houver uma conta aguardando verificação, enviaremos um link. Confira seu email e a pasta de spam. Se você acabou de solicitar, aguarde um minuto antes de pedir outro.");
      setRemaining(60);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Não foi possível solicitar o email.");
      if (err instanceof ApiError) setRemaining(err.retryAfter);
    } finally {
      setBusy(false);
    }
  }

  return <details className="verification-resend" open={expanded || undefined}>
    <summary>Não recebeu o email ou o link expirou?</summary>
    <p>Informe o email usado no cadastro para solicitar um novo link. Seu perfil e seu período de teste são mantidos.</p>
    <form onSubmit={resend} aria-busy={busy}>
      <label>Email do cadastro<input name="email" type="email" required maxLength={254} autoComplete="email" /></label>
      <button className="secondary-button" disabled={busy || remaining > 0}>
        {busy ? "Solicitando…" : remaining > 0 ? `Aguarde ${remaining}s para reenviar` : "Reenviar verificação"}
      </button>
    </form>
    {message && <p role="status">{message}</p>}
    {error && <p className="form-error" role="alert">{error}</p>}
  </details>;
}
