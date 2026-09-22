"use client";
import { useEffect, useState, type FormEvent } from "react";
import { api } from "../lib/api";
type Mode = "cadastro" | "entrar" | "verificar" | "recuperar";
export default function AccountForm({ mode }: { mode: Mode }) {
  const [token, setToken] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  useEffect(() => { const value = new URLSearchParams(window.location.hash.slice(1)).get("token"); if (value) { setToken(value); window.history.replaceState(null, "", window.location.pathname); } }, []);
  const titles = { cadastro: "Comece seus 7 dias grátis", entrar: "Bem-vindo de volta", verificar: "Verifique seu email", recuperar: "Recupere seu acesso" };
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError(""); setMessage("");
    const data = Object.fromEntries(new FormData(event.currentTarget));
    try {
      if (mode === "cadastro") { await api("/auth/register", "POST", data); setMessage("Se o cadastro puder ser realizado, enviaremos um link válido por 15 minutos. Confira seu email."); }
      if (mode === "entrar") { await api("/auth/login", "POST", data); window.location.assign("/painel"); }
      if (mode === "verificar") { await api("/auth/verify", "POST", { token }); setMessage("Email verificado. Você já pode entrar."); setToken(""); }
      if (mode === "recuperar") {
        if (token) { await api("/auth/password-reset/confirm", "POST", { token, password: data.password }); setMessage("Senha atualizada. Entre com sua nova senha."); setToken(""); }
        else { await api("/auth/password-reset", "POST", data); setMessage("Se houver uma conta, enviaremos as instruções por email."); }
      }
    } catch (err) { setError(err instanceof Error ? err.message : "Serviço indisponível."); } finally { setBusy(false); }
  }
  return <main className="account-shell"><a className="brand-mark" href="/">agendou</a><section className="account-card"><h1>{titles[mode]}</h1>
    {mode === "cadastro" && <p>Teste o Premium/Top. Seus dados ficam preservados após o teste.</p>}
    <form onSubmit={submit} aria-busy={busy}>
      {mode === "cadastro" && <><label>Nome do negócio<input name="name" required maxLength={100} autoComplete="organization" /></label><label>Endereço da sua página<input name="slug" required minLength={3} maxLength={60} pattern="[a-z0-9]+(-[a-z0-9]+)*" aria-describedby="slug-help" /></label><small id="slug-help">Use letras minúsculas, números e hífens.</small></>}
      {(mode === "cadastro" || mode === "entrar" || (mode === "recuperar" && !token)) && <label>Email<input name="email" type="email" required maxLength={254} autoComplete="email" /></label>}
      {(mode === "cadastro" || mode === "entrar" || (mode === "recuperar" && token)) && <label>{mode === "recuperar" ? "Nova senha" : "Senha"}<input name="password" type="password" required minLength={mode === "entrar" ? 1 : 12} maxLength={64} autoComplete={mode === "entrar" ? "current-password" : "new-password"} /><small>{mode !== "entrar" && "Use entre 12 e 64 caracteres."}</small></label>}
      {mode === "verificar" && !token && !message && <p>Abra o link enviado ao seu email para continuar.</p>}
      <button className="primary-button" disabled={busy || (mode === "verificar" && !token)}>{busy ? "Aguarde…" : mode === "verificar" ? "Confirmar email" : mode === "entrar" ? "Entrar" : mode === "cadastro" ? "Criar conta" : "Continuar"}</button>
    </form>
    {error && <p className="form-error" role="alert">{error}</p>}{message && <p role="status">{message}</p>}
    <nav className="account-links"><a href="/entrar">Entrar</a><a href="/cadastro">Criar conta</a><a href="/recuperar">Esqueci minha senha</a></nav>
  </section></main>;
}
