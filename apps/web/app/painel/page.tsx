"use client";
import { useEffect, useState, type FormEvent } from "react";
import { api } from "../lib/api";
import Brand from "../components/Brand";
type Subscription = { status: string; planCode: string; trialEndsAt: string | null; paidUntil: string | null };
type Profile = { name: string; slug: string; description: string; timezone: string };
const states: Record<string,string> = { TRIAL_ACTIVE: "Teste Premium ativo", TRIAL_EXPIRING: "Seu teste termina em breve", TRIAL_EXPIRED_BLOCKED: "Seu teste Premium terminou", PAID_ACTIVE: "Assinatura ativa", PAST_DUE: "Pagamento pendente", SUSPENDED: "Assinatura suspensa", CANCELED: "Assinatura cancelada" };
export default function Dashboard() {
 const [subscription,setSubscription]=useState<Subscription|null>(null);
 const [profile,setProfile]=useState<Profile|null>(null);
 const [error,setError]=useState(""); const [message,setMessage]=useState(""); const [busy,setBusy]=useState(false);
 async function load() { setError(""); try {const [s,p]=await Promise.all([api("/admin/subscription"),api("/admin/profile")]);setSubscription(s);setProfile(p);} catch(e){setError(e instanceof Error?e.message:"Falha ao carregar.");} }
 useEffect(()=>{void load();},[]);
 const operational=subscription && ["TRIAL_ACTIVE","TRIAL_EXPIRING","PAID_ACTIVE"].includes(subscription.status);
 async function save(e:FormEvent<HTMLFormElement>) {e.preventDefault();setBusy(true);setError("");setMessage("");try {setProfile(await api("/admin/profile","PATCH",Object.fromEntries(new FormData(e.currentTarget))));setMessage("Perfil salvo.");}catch(e){await load();setError(e instanceof Error?e.message:"Falha ao salvar.");}finally{setBusy(false);}}
 async function logout() {try{await api("/auth/logout","POST");window.location.assign("/entrar");}catch(e){setError(e instanceof Error?e.message:"Falha ao sair.");}}
 return <main className="dashboard-shell"><nav className="topbar"><Brand /><button className="secondary-button" onClick={logout}>Sair</button></nav>
 <h1>Seu negócio, no seu ritmo</h1>
 {error && <div role="alert" className="form-error">{error} <button onClick={load}>Tentar novamente</button></div>}
 {!subscription && !error && <p role="status">Carregando seu painel…</p>}
 {subscription && <section className={`subscription-banner ${operational ? "" : "blocked"}`}><h2>{states[subscription.status] || subscription.status}</h2>
 {subscription.trialEndsAt && subscription.status.startsWith("TRIAL_") && <p>Fim do teste: {new Date(subscription.trialEndsAt).toLocaleString("pt-BR")}</p>}
 {subscription.paidUntil && <p>Vigência: {new Date(subscription.paidUntil).toLocaleDateString("pt-BR")}</p>}
 {!operational && <p>Seus dados e sua página estão preservados. Para reativar, solicite a regularização da assinatura à administração do Agendou.</p>}</section>}
 {profile && <section className="account-card"><h2>Perfil do negócio</h2><p>Endereço reservado: /a/{profile.slug}</p><form onSubmit={save}><fieldset disabled={busy || !operational}>
 <label>Nome<input name="name" defaultValue={profile.name} required maxLength={100}/></label><label>Descrição<textarea name="description" defaultValue={profile.description} maxLength={2000}/></label><label>Fuso horário<input name="timezone" defaultValue={profile.timezone} required maxLength={100}/></label><button className="primary-button">{busy?"Salvando…":"Salvar perfil"}</button></fieldset></form>{message && <p role="status">{message}</p>}</section>}
 </main>;
}


