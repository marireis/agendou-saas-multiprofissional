"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import Brand from "../components/Brand";
import { api, ApiError } from "../lib/api";

type Access = { enrolled: boolean; verified: boolean };
type Tenant = { id: string; slug: string; display_name: string; subscription_status: string; plan_code: string; trial_ends_at: string | null; paid_until: string | null };
type Decision = { id: string; action: string; reason: string; reference: string | null; amount_cents: number | null; created_at: string };
type Detail = { tenant: Tenant; subscription: { status: string; planCode: string; paidUntil: string | null; trialEndsAt: string | null }; decisions: Decision[] };
type Audit = { id: string; actor_id: string; action: string; tenant_id: string | null; correlation_id: string; created_at: string };
const statuses: Record<string,string> = { TRIAL_ACTIVE: "Teste ativo", TRIAL_EXPIRING: "Teste terminando", TRIAL_EXPIRED_BLOCKED: "Teste vencido", PAID_ACTIVE: "Assinatura ativa", PAST_DUE: "Pagamento vencido", SUSPENDED: "Suspensa", CANCELED: "Cancelada" };
const actions: Record<string,string> = { CONFIRM_PAYMENT: "Confirmar pagamento", SUSPEND: "Suspender operação", REACTIVATE: "Retirar suspensão" };
const date = (value: string | null) => value ? new Date(value).toLocaleString("pt-BR") : "—";
const money = (value: number) => (value / 100).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });

export default function PlatformPage() {
  const [access,setAccess] = useState<Access | null>(null);
  const [secret,setSecret] = useState("");
  const [account,setAccount] = useState("");
  const [tenants,setTenants] = useState<Tenant[]>([]);
  const [offset,setOffset] = useState(0);
  const [detail,setDetail] = useState<Detail | null>(null);
  const [audit,setAudit] = useState<Audit[] | null>(null);
  const [auditOffset,setAuditOffset] = useState(0);
  const [action,setAction] = useState("CONFIRM_PAYMENT");
  const [busy,setBusy] = useState(false);
  const [error,setError] = useState("");
  const [message,setMessage] = useState("");
  const [remaining,setRemaining] = useState(0);
  const pending = useRef<{ payload: string; id: string } | null>(null);
  function fail(e: unknown) {
    setError(e instanceof Error ? e.message : "Não foi possível concluir.");
    if(e instanceof ApiError) {
      setRemaining(e.retryAfter);
      if(e.status===403) {setAccess(null);setDetail(null);setTenants([]);setAudit(null);}
    }
  }
  async function loadTenants(page=offset) {setTenants(await api(`/platform/tenants?offset=${page}`));setOffset(page);}
  async function refresh() {
    setBusy(true);setError("");
    try {const current: Access=await api("/platform/session");setAccess(current);if(current.verified) await loadTenants();}
    catch(e) {fail(e);} finally {setBusy(false);}
  }
  useEffect(() => {void refresh();}, []); // Initial session check; server remains authoritative on every action.
  useEffect(() => {if(remaining<=0)return;const timer=window.setTimeout(()=>setRemaining(n=>Math.max(0,n-1)),1000);return ()=>window.clearTimeout(timer);},[remaining]);
  async function enroll(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();const form=event.currentTarget;setBusy(true);setError("");
    try {const result=await api("/platform/mfa/enrollment","POST",Object.fromEntries(new FormData(form)));setSecret(result.secret);setAccount(result.account);form.reset();}
    catch(e){fail(e);}finally{setBusy(false);}
  }
  async function verify(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();setBusy(true);setError("");
    try {await api("/platform/mfa/verify","POST",Object.fromEntries(new FormData(event.currentTarget)));setSecret("");setAccount("");await refresh();setMessage("MFA confirmado. Acesso administrativo liberado por 15 minutos.");}
    catch(e){fail(e);}finally{setBusy(false);}
  }
  async function select(tenant: Tenant) {
    setBusy(true);setError("");setMessage("");setDetail(null);pending.current=null;
    try {const result: Detail=await api(`/platform/tenants/${tenant.id}`);setDetail(result);setAction(result.subscription.status==="SUSPENDED"?"REACTIVATE":"CONFIRM_PAYMENT");}
    catch(e){fail(e);}finally{setBusy(false);}
  }
  async function decide(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();if(!detail)return;
    const form=event.currentTarget;const data=Object.fromEntries(new FormData(form));
    const payment=action==="CONFIRM_PAYMENT";
    const amount=String(data.amount || "").replace(",", ".");
    if(payment && !/^\d+(\.\d{1,2})?$/.test(amount)) {setError("Informe um valor com até duas casas decimais.");return;}
    const amountCents=Math.round(Number(amount)*100);
    const body={action,reason:data.reason,...(payment?{reference:data.reference,amountCents,planCode:data.planCode}:{})};
    const payload=JSON.stringify({tenant:detail.tenant.id,...body});
    if(!pending.current || pending.current.payload!==payload) pending.current={payload,id:crypto.randomUUID()};
    setBusy(true);setError("");setMessage("");
    try {
      await api(`/platform/tenants/${detail.tenant.id}/decisions`,"POST",{...body,id:pending.current.id});
      pending.current=null;form.reset();
      setDetail(await api(`/platform/tenants/${detail.tenant.id}`));await loadTenants();
      setMessage("Decisão registrada com auditoria. Os dados do profissional foram preservados.");
    }catch(e){fail(e);}finally{setBusy(false);}
  }
  async function pageTenants(page: number) {setBusy(true);setError("");try{await loadTenants(page);}catch(e){fail(e);}finally{setBusy(false);}}
  async function showAudit(page=0) {setBusy(true);setError("");try{setAudit(await api(`/platform/audit?offset=${page}`));setAuditOffset(page);}catch(e){fail(e);}finally{setBusy(false);}}
  async function logout() {setBusy(true);try{await api("/auth/logout","POST");window.location.assign("/entrar?destino=plataforma");}catch(e){fail(e);setBusy(false);}}

  return <main className="dashboard-shell platform-shell">
    <nav className="topbar"><Brand /><div className="platform-actions"><a href="/painel">Painel profissional</a><button className="secondary-button" onClick={logout} disabled={busy}>Sair</button></div></nav>
    <div className="platform-heading"><div><p className="eyebrow">ADMINISTRAÇÃO DO AGENDOU</p><h1>Visão da plataforma</h1><p>Assinaturas, acesso e histórico de decisões.</p></div><button className="secondary-button" onClick={refresh} disabled={busy}>Atualizar acesso</button></div>
    {error && <p className="form-error" role="alert">{error}</p>}{message && <p role="status">{message}</p>}
    {!access && !error && <p role="status">Verificando acesso…</p>}
    {!access && error && <p>Entre com uma conta habilitada como superadmin. <a href="/entrar?destino=plataforma">Ir para o login</a></p>}
    {access && !access.verified && <section className="account-card platform-mfa"><h2>{access.enrolled ? "Confirme seu acesso" : "Proteja seu acesso administrativo"}</h2>
      <p>Use um aplicativo autenticador. A confirmação libera esta sessão por 15 minutos.</p>
      {!access.enrolled && !secret && <form onSubmit={enroll}><label>Confirme sua senha<input type="password" name="password" required maxLength={64} autoComplete="current-password" /></label><button className="primary-button" disabled={busy || remaining>0}>Configurar autenticador</button></form>}
      {secret && <div className="mfa-setup"><p>No autenticador, adicione uma conta com chave manual:</p><dl><dt>Nome</dt><dd>Agendou — {account}</dd><dt>Tipo</dt><dd>Baseado em tempo · 6 dígitos · SHA1 · 30 segundos</dd><dt>Chave</dt><dd><code className="mfa-secret">{secret}</code></dd></dl><p>Guarde a conta no autenticador antes de confirmar. Esta chave não aparecerá após a ativação.</p></div>}
      {(access.enrolled || secret) && <form onSubmit={verify}><label>Código do autenticador<input name="code" required inputMode="numeric" pattern="[0-9]{6}" minLength={6} maxLength={6} autoComplete="one-time-code" /></label><button className="primary-button" disabled={busy || remaining>0}>Confirmar MFA</button></form>}
      {remaining>0 && <p role="status">Aguarde {remaining}s antes de tentar novamente.</p>}
    </section>}
    {access?.verified && <>
      <section className="platform-panel"><div className="platform-heading"><h2>Profissionais</h2><button className="secondary-button" disabled={busy} onClick={()=>showAudit()}>Consultar auditoria</button></div>
        {tenants.length===0 ? <p>Nenhum profissional nesta página.</p> : <div className="platform-table-wrap"><table className="platform-table"><thead><tr><th>Negócio</th><th>Plano</th><th>Estado registrado</th><th>Vigência</th><th>Ação</th></tr></thead><tbody>{tenants.map(t=><tr key={t.id}><td><strong>{t.display_name}</strong><small>/{t.slug}</small></td><td>{t.plan_code}</td><td>{statuses[t.subscription_status] || t.subscription_status}</td><td>{date(t.paid_until || t.trial_ends_at)}</td><td><button className="secondary-button" disabled={busy} onClick={()=>select(t)}>Gerenciar<span className="sr-only"> {t.display_name}</span></button></td></tr>)}</tbody></table></div>}
        <div className="platform-actions"><button className="secondary-button" disabled={busy || offset===0} onClick={()=>pageTenants(Math.max(0,offset-50))}>Anterior</button><span>Página {offset/50+1}</span><button className="secondary-button" disabled={busy || tenants.length<50 || offset>=100000} onClick={()=>pageTenants(offset+50)}>Próxima</button></div>
      </section>
      {detail && <section className="platform-panel" key={detail.tenant.id}><h2>{detail.tenant.display_name}</h2><p>{statuses[detail.subscription.status]} · {detail.subscription.planCode}</p><p>Fim do teste: {date(detail.subscription.trialEndsAt)} · Vigência paga: {date(detail.subscription.paidUntil)}</p>
        <form className="platform-decision" onSubmit={decide}><fieldset disabled={busy}>
          <label>Decisão<select value={action} onChange={e=>setAction(e.target.value)}>{Object.entries(actions).map(([value,label])=><option key={value} value={value}>{label}</option>)}</select></label>
          {action==="CONFIRM_PAYMENT" && <><p>Registre somente pagamentos já conferidos. A confirmação adiciona 30 dias à vigência paga atual ou à data de hoje, se vencida.</p><label>Plano<select name="planCode" defaultValue={detail.subscription.planCode}><option value="BASIC">Básico</option><option value="INTERMEDIATE">Intermediário</option><option value="PREMIUM_TOP">Premium/Top</option></select></label><label>Valor recebido (R$)<input name="amount" inputMode="decimal" placeholder="99,90" required maxLength={12}/></label><label>Referência bancária única<input name="reference" required minLength={3} maxLength={100}/></label></>}
          {action==="REACTIVATE" && <p>Retira a suspensão e respeita a vigência original. Se ela venceu, o profissional permanece bloqueado até a confirmação do pagamento.</p>}
          {action==="SUSPEND" && <p>Impede a operação do profissional e preserva seus dados.</p>}
          <label>Justificativa<textarea name="reason" required minLength={10} maxLength={500} /></label><label className="platform-confirm"><input type="checkbox" required/>Revisei o profissional selecionado e confirmo esta decisão.</label><button className="primary-button">{busy?"Registrando…":"Registrar decisão"}</button>
        </fieldset></form>
        <h3>Últimas decisões</h3>{detail.decisions.length===0?<p>Nenhuma decisão registrada.</p>:<ul className="platform-history">{detail.decisions.map(d=><li key={d.id}><strong>{actions[d.action]} · {date(d.created_at)}</strong><p>{d.reason}</p>{d.amount_cents!=null && <small>{money(d.amount_cents)} · Referência: {d.reference}</small>}</li>)}</ul>}
      </section>}
      {audit && <section className="platform-panel"><h2>Auditoria da plataforma</h2><p>Identificadores permitem rastrear ações sem exibir dados de clientes.</p><ul className="platform-history">{audit.map(a=><li key={a.id}><strong>{a.action} · {date(a.created_at)}</strong><small>Operador: {a.actor_id}</small>{a.tenant_id && <small>Profissional: {a.tenant_id}</small>}<small>Requisição: {a.correlation_id}</small></li>)}</ul><div className="platform-actions"><button className="secondary-button" disabled={busy || auditOffset===0} onClick={()=>showAudit(Math.max(0,auditOffset-50))}>Anterior</button><span>Página {auditOffset/50+1}</span><button className="secondary-button" disabled={busy || audit.length<50 || auditOffset>=100000} onClick={()=>showAudit(auditOffset+50)}>Próxima</button></div></section>}
    </>}
  </main>;
}
