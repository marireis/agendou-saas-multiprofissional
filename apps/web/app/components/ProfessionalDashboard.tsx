"use client";
import {useEffect,useState} from "react";
import Link from "next/link";
import Brand from "./Brand";
import ProfessionalProfile,{type Profile} from "./ProfessionalProfile";
import ServiceCatalog,{type Catalog} from "./ServiceCatalog";
import {api} from "../lib/api";
import PaymentSettings from "./PaymentSettings";
import Publication from "./Publication";
type Section="overview"|"profile"|"services"|"subscription"|"payments"|"publication";
type Subscription={status:string;planCode:string;trialEndsAt:string|null;paidUntil:string|null};
const states:Record<string,string>={TRIAL_ACTIVE:"Teste Premium ativo",TRIAL_EXPIRING:"Seu teste termina em breve",TRIAL_EXPIRED_BLOCKED:"Seu teste Premium terminou",PAID_ACTIVE:"Assinatura ativa",PAST_DUE:"Pagamento pendente",SUSPENDED:"Assinatura suspensa",CANCELED:"Assinatura cancelada"};
const navigation=[{id:"overview",href:"/painel",name:"Visão geral"},{id:"profile",href:"/painel/minha-pagina",name:"Minha página"},{id:"services",href:"/painel/servicos",name:"Serviços"},{id:"payments",href:"/painel/pagamentos",name:"PIX e política"},{id:"publication",href:"/painel/publicacao",name:"Publicação"},{id:"subscription",href:"/painel/assinatura",name:"Assinatura"}];
export default function ProfessionalDashboard({section}:{section:Section}){
 const [subscription,setSubscription]=useState<Subscription|null>(null),[profile,setProfile]=useState<Profile|null>(null),[catalog,setCatalog]=useState<Catalog|null>(null),[error,setError]=useState("");
 async function load(){setError("");try{const [s,p,c]=await Promise.all([api("/admin/subscription"),api("/admin/profile"),api("/admin/services")]);setSubscription(s);setProfile(p);setCatalog(c);}catch(e){setError(e instanceof Error?e.message:"Falha ao carregar.");}}
 useEffect(()=>{void load();},[]);
 const operational=Boolean(subscription&&["TRIAL_ACTIVE","TRIAL_EXPIRING","PAID_ACTIVE"].includes(subscription.status));
 async function logout(){try{await api("/auth/logout","POST");window.location.assign("/entrar");}catch(e){setError(e instanceof Error?e.message:"Falha ao sair.");}}
 return <div className="professional-shell"><aside className="professional-sidebar"><Brand/><p>PAINEL PROFISSIONAL</p><nav aria-label="Áreas do painel">{navigation.map(item=><Link key={item.id} href={item.href} aria-current={section===item.id?"page":undefined}>{item.name}</Link>)}</nav><button className="secondary-button" onClick={logout}>Sair</button></aside><main className="professional-content">
 {error&&<div role="alert" className="form-error">{error} <button onClick={load}>Tentar novamente</button></div>}
 {!subscription&&!error&&<p role="status">Carregando seu painel…</p>}
 {subscription&&<>{(!operational||section==="subscription")&&<section className={`subscription-banner ${operational?"":"blocked"}`}><h2>{states[subscription.status]||subscription.status}</h2><p>Plano: {subscription.planCode}</p>{subscription.trialEndsAt&&<p>Fim do teste: {new Date(subscription.trialEndsAt).toLocaleString("pt-BR")}</p>}{subscription.paidUntil&&<p>Vigência paga: {new Date(subscription.paidUntil).toLocaleString("pt-BR")}</p>}{!operational&&<p>Seus dados estão preservados. Solicite a regularização à administração do Agendou.</p>}</section>}
 {section==="overview"&&<><p className="eyebrow">{profile?.name}</p><h1>Seu negócio, no seu ritmo</h1><p>Acompanhe sua configuração e escolha a área que deseja gerenciar.</p><div className="overview-cards"><Link href="/painel/minha-pagina"><small>Minha página</small><strong>{profile?.profileProgress??0}%</strong><span>do perfil preenchido</span></Link><Link href="/painel/servicos"><small>Serviços</small><strong>{catalog?.activeCount??0}</strong><span>ativos no catálogo</span></Link><Link href="/painel/assinatura"><small>Assinatura</small><strong className="overview-status">{states[subscription.status]}</strong><span>Consultar plano e vigência</span></Link></div><section className="profile-panel"><h2>Prepare seu atendimento</h2><p>Complete seu perfil e cadastre seus serviços. Confira a prévia e os requisitos na área Publicação.</p><div className="profile-actions"><Link className="primary-button" href="/painel/minha-pagina">Configurar minha página</Link><Link className="secondary-button" href="/painel/servicos">Gerenciar serviços</Link></div></section></>}
 {section==="profile"&&profile&&<><h1>Minha página</h1><ProfessionalProfile profile={profile} operational={operational} onChange={setProfile} onBlocked={load}/></>}
 {section==="services"&&<ServiceCatalog operational={operational} onBlocked={load}/>}
 {section==="publication"&&<Publication/>}
 {section==="payments"&&<PaymentSettings operational={operational} onBlocked={load}/>}
 {section==="subscription"&&<section className="profile-panel"><h1>Sua assinatura</h1><p>O teste Premium/Top dura 7 dias. Seus dados permanecem preservados após o vencimento.</p><p>A confirmação de pagamento é feita pela administração do Agendou após conferência. Não há cobrança automática nesta etapa.</p></section>}
 </>}
 </main></div>;
}
