"use client";
import {useEffect,useState,type FormEvent} from "react";
import {api,ApiError} from "../lib/api";
type Settings={configured:boolean;version:number;keyType:string;pixKey:string;recipientName:string;paymentInstructions:string;cancellationPolicy:string;enabled:boolean;updatedAt?:string};
type History={version:number;actorId:string;changedFields:string[];enabled:boolean;createdAt:string;correlationId:string};
const empty:Settings={configured:false,version:0,keyType:"EMAIL",pixKey:"",recipientName:"",paymentInstructions:"",cancellationPolicy:"",enabled:true};
const types:Record<string,string>={EMAIL:"Email",PHONE:"Telefone com código do país",CPF:"CPF",CNPJ:"CNPJ",RANDOM:"Chave aleatória"};
const fields:Record<string,string>={key_type:"Tipo de chave",pix_key:"Chave PIX",recipient_name:"Recebedor",payment_instructions:"Instruções de pagamento",cancellation_policy:"Política de cancelamento",enabled:"Disponibilidade do PIX"};
export default function PaymentSettings({operational,onBlocked}:{operational:boolean;onBlocked:()=>Promise<void>}){
 const [saved,setSaved]=useState<Settings|null>(null),[draft,setDraft]=useState<Settings>(empty),[history,setHistory]=useState<History[]>([]),[offset,setOffset]=useState(0);
 const [busy,setBusy]=useState(false),[error,setError]=useState(""),[message,setMessage]=useState(""),[confirmed,setConfirmed]=useState(false),[reason,setReason]=useState(""),[conflict,setConflict]=useState(false);
 async function load(){const value={...empty,...await api("/admin/payment-settings")};setSaved(value);setDraft(value);setConfirmed(false);setReason("");setConflict(false);setHistory(await api("/admin/payment-settings/history"));setOffset(0);}
 useEffect(()=>{load().catch(e=>setError(e instanceof Error?e.message:"Falha ao carregar."));},[]);
 function field(key:keyof Settings,value:string|boolean){setDraft(old=>({...old,[key]:value}));setConfirmed(false);setMessage("");}
 async function reload(){if(saved&&JSON.stringify(saved)!==JSON.stringify(draft)&&!window.confirm("Recarregar os dados salvos e descartar a edição atual?"))return;setBusy(true);setError("");try{await load();}catch(e){setError(e instanceof Error?e.message:"Falha ao carregar.");}finally{setBusy(false);}}
 async function save(event:FormEvent<HTMLFormElement>){event.preventDefault();if(busy||!saved)return;setBusy(true);setError("");setMessage("");
  try{const value:Settings=await api("/admin/payment-settings","PUT",{version:saved.version,keyType:draft.keyType,pixKey:draft.pixKey,recipientName:draft.recipientName,paymentInstructions:draft.paymentInstructions,cancellationPolicy:draft.cancellationPolicy,enabled:draft.enabled,confirmed,changeReason:reason});setSaved(value);setDraft(value);setConfirmed(false);setReason("");setMessage("Configuração salva com histórico de alterações.");setHistory(await api("/admin/payment-settings/history"));setOffset(0);}
  catch(e){setError(e instanceof Error?e.message:"Falha ao salvar.");if(e instanceof ApiError&&e.status===409)setConflict(true);if(e instanceof ApiError&&e.status===403)await onBlocked();}finally{setBusy(false);}
 }
 async function page(next:number){setBusy(true);setError("");try{setHistory(await api(`/admin/payment-settings/history?offset=${next}`));setOffset(next);}catch(e){setError(e instanceof Error?e.message:"Falha ao carregar histórico.");}finally{setBusy(false);}}
 return <section><div className="platform-heading"><div><h1>PIX e política</h1><p>Prepare as informações de pagamento que seus clientes receberão.</p></div><button className="secondary-button" disabled={busy} onClick={reload}>Recarregar dados salvos</button></div>
 {error&&<p className="form-error" role="alert">{error}</p>}{message&&<p role="status">{message}</p>}{!saved&&!error&&<p role="status">Carregando configuração…</p>}
 {saved&&<><section className="profile-panel"><h2>{saved.configured?"Dados de recebimento":"Configure seu PIX"}</h2><p>Cadastre uma chave já registrada no seu banco e o nome exato do recebedor. O Agendou verifica o formato, mas não consulta o banco nem confirma titularidade ou pagamento.</p>
 {!operational&&<p>Sua assinatura permite consultar os dados, mas precisa estar ativa para alterá-los.</p>}
 {conflict&&<p role="alert">Há uma versão mais recente. Use “Recarregar dados salvos”, confira as informações e refaça sua alteração.</p>}
 <form onSubmit={save} aria-busy={busy}><fieldset disabled={busy||!operational||conflict}>
 <div className="profile-fields"><label>Tipo de chave<select value={draft.keyType} onChange={e=>{field("keyType",e.target.value);field("pixKey","");}}>{Object.entries(types).map(([value,label])=><option key={value} value={value}>{label}</option>)}</select></label><label>Chave PIX<input value={draft.pixKey} onChange={e=>field("pixKey",e.target.value)} required maxLength={100} autoComplete="off" spellCheck={false} placeholder={draft.keyType==="PHONE"?"+55 11 99999-9999":"Chave cadastrada no banco"}/></label></div>
 <label>Nome do recebedor<input value={draft.recipientName} onChange={e=>field("recipientName",e.target.value)} required minLength={2} maxLength={100} autoComplete="off"/></label>
 <label>Instruções de pagamento<textarea value={draft.paymentInstructions} onChange={e=>field("paymentInstructions",e.target.value)} required minLength={10} maxLength={2000} rows={4} placeholder="Explique como o cliente deve pagar e como será feita a conferência."/></label><small>A entrada de 50% a 100% é definida em cada serviço. A reserva só será confirmada após conferência manual do pagamento.</small>
 <label>Política de cancelamento e reagendamento<textarea value={draft.cancellationPolicy} onChange={e=>field("cancellationPolicy",e.target.value)} required minLength={10} maxLength={4000} rows={5} placeholder="Descreva prazos, como solicitar cancelamento/reagendamento e como tratar devoluções."/></label><small>Escreva as regras do seu negócio. Este texto não executa cancelamentos ou devoluções automaticamente.</small>
 <label className="service-active"><input type="checkbox" checked={draft.enabled} onChange={e=>field("enabled",e.target.checked)}/>Usar esta configuração PIX nas futuras reservas</label><small>Desativar preserva o histórico. Esta opção não publica a página nem altera sua assinatura do Agendou.</small>
 <label>Motivo da configuração ou alteração<textarea value={reason} onChange={e=>setReason(e.target.value)} required minLength={10} maxLength={500} rows={2} placeholder="Ex.: Cadastro inicial dos dados de recebimento"/></label><small>Não repita a chave ou dados pessoais no motivo.</small>
 <label className="service-active"><input type="checkbox" checked={confirmed} onChange={e=>setConfirmed(e.target.checked)} required/>Conferi a chave, o recebedor e as regras acima.</label><button className="primary-button" disabled={!confirmed}>Salvar configuração</button>
 </fieldset></form></section>
 <section className="profile-panel"><h2>Histórico de alterações</h2><p>Versões preservadas para manter as condições de futuras reservas. Chaves anteriores não são exibidas neste histórico.</p>{saved.configured&&<p>Versão atual: {saved.version} · {saved.enabled?"Habilitada":"Desabilitada"}</p>}
 {history.length===0?<p>Nenhuma alteração nesta página.</p>:<ul className="platform-history">{history.map(item=><li key={item.version}><strong>Versão {item.version} · {new Date(item.createdAt).toLocaleString("pt-BR")}</strong><p>{item.changedFields.map(name=>fields[name]||name).join(" · ")}</p><small>Operador: {item.actorId}</small><small>Requisição: {item.correlationId}</small></li>)}</ul>}
 <div className="platform-actions"><button className="secondary-button" disabled={busy||offset===0} onClick={()=>page(Math.max(0,offset-50))}>Anterior</button><span>Página {offset/50+1}</span><button className="secondary-button" disabled={busy||history.length<50||offset>=100000} onClick={()=>page(offset+50)}>Próxima</button></div>
 </section></>}
 </section>;
}
