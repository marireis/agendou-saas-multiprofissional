"use client";
import { useEffect, useState, type FormEvent } from "react";
import { api } from "../lib/api";
type Service = { id:string; name:string; description:string; durationMinutes:number; priceCents:number; bufferBeforeMinutes:number; bufferAfterMinutes:number; depositPercent:number; depositCents:number; active:boolean; version:number };
export type Catalog = { items:Service[]; total:number; activeCount:number };
const money=(cents:number)=>(cents/100).toLocaleString("pt-BR",{style:"currency",currency:"BRL"});
export default function ServiceCatalog({operational,onBlocked}:{operational:boolean;onBlocked:()=>Promise<void>}) {
 const [catalog,setCatalog]=useState<Catalog|null>(null),[offset,setOffset]=useState(0),[editing,setEditing]=useState<Service|null>(null);
 const [formOpen,setFormOpen]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState(""),[message,setMessage]=useState("");
 async function load(page=offset){setCatalog(await api(`/admin/services?offset=${page}`));setOffset(page);}
 useEffect(()=>{load(0).catch(e=>setError(e.message));},[]);
 async function refresh(page=offset){setBusy(true);setError("");try{await load(page);}catch(e){setError(e instanceof Error?e.message:"Falha ao carregar.");}finally{setBusy(false);}}
 async function save(event:FormEvent<HTMLFormElement>){
  event.preventDefault();if(busy)return;const data=Object.fromEntries(new FormData(event.currentTarget));
  const amount=String(data.price).trim().replace(",",".");
  if(!/^\d+(\.\d{1,2})?$/.test(amount)){setError("Informe o preço sem separador de milhar, com até duas casas decimais.");return;}
  const [whole,fraction=""]=amount.split(".");const priceCents=Number(whole)*100+Number(fraction.padEnd(2,"0"));
  const body={name:data.name,description:data.description,priceCents,durationMinutes:Number(data.durationMinutes),bufferBeforeMinutes:Number(data.bufferBeforeMinutes),bufferAfterMinutes:Number(data.bufferAfterMinutes),depositPercent:Number(data.depositPercent),active:data.active==="on",...(editing?{version:editing.version}:{})};
  setBusy(true);setError("");setMessage("");
  try{await api(editing?`/admin/services/${editing.id}`:"/admin/services",editing?"PUT":"POST",body);setFormOpen(false);setEditing(null);setMessage("Serviço salvo.");await load();}
  catch(e){setError(e instanceof Error?e.message:"Falha ao salvar.");await onBlocked();}finally{setBusy(false);}
 }
 return <section><div className="platform-heading"><div><h1>Serviços</h1><p>Organize o que você oferece e o tempo de cada atendimento.</p></div><button className="primary-button" disabled={busy||!operational} onClick={()=>{setEditing(null);setFormOpen(true);setError("");setMessage("");}}>Novo serviço</button></div>
 {error&&<p className="form-error" role="alert">{error}</p>}{message&&<p role="status">{message}</p>}
 {!operational&&<p>Sua assinatura permite consultar os serviços, mas precisa estar ativa para alterá-los.</p>}
 {formOpen&&<section className="profile-panel"><h2>{editing?"Editar serviço":"Cadastrar serviço"}</h2><form key={editing?.id||"new"} onSubmit={save}><fieldset disabled={busy||!operational}>
 <label>Nome<input name="name" defaultValue={editing?.name||""} required maxLength={100}/></label><label>Descrição<textarea name="description" defaultValue={editing?.description||""} maxLength={2000}/></label>
 <div className="profile-fields"><label>Duração (minutos)<input name="durationMinutes" type="number" min={5} max={480} step={1} defaultValue={editing?.durationMinutes||30} required/></label><label>Preço (R$)<input name="price" inputMode="decimal" defaultValue={editing?(editing.priceCents/100).toFixed(2).replace(".",","):""} placeholder="99,90" required maxLength={12}/></label></div>
 <div className="profile-fields"><label>Intervalo antes (minutos)<input name="bufferBeforeMinutes" type="number" min={0} max={240} step={1} defaultValue={editing?.bufferBeforeMinutes||0} required/></label><label>Intervalo depois (minutos)<input name="bufferAfterMinutes" type="number" min={0} max={240} step={1} defaultValue={editing?.bufferAfterMinutes||0} required/></label></div><small>Tempo reservado para preparação ou pausa, além da duração do atendimento.</small>
 <label>Entrada exigida (%)<input name="depositPercent" type="number" min={50} max={100} step={1} defaultValue={editing?.depositPercent||50} required/></label>
 <label className="service-active"><input type="checkbox" name="active" defaultChecked={editing?.active??true}/>Serviço ativo</label><small>Desmarque para inativar. O registro será preservado; isso não publica sua agenda.</small>
 <div className="profile-actions"><button className="primary-button">{busy?"Salvando…":"Salvar serviço"}</button><button type="button" className="secondary-button" onClick={()=>setFormOpen(false)}>Cancelar</button></div>
 </fieldset></form></section>}
 <section className="profile-panel"><div className="platform-heading"><h2>Seu catálogo {catalog?`(${catalog.total})`:""}</h2><button className="secondary-button" disabled={busy} onClick={()=>refresh()}>Recarregar lista</button></div>
 {!catalog&&!error&&<p role="status">Carregando serviços…</p>}
 {catalog&&<><p>{catalog.activeCount} serviço(s) ativo(s).</p>{catalog.total===0?<p>Cadastre seu primeiro serviço para começar a preparar sua agenda.</p>:<div className="platform-table-wrap"><table className="platform-table"><thead><tr><th>Serviço</th><th>Duração e intervalos</th><th>Preço / entrada</th><th>Estado</th><th>Ação</th></tr></thead><tbody>{catalog.items.map(s=><tr key={s.id}><td><strong>{s.name}</strong><p className="service-description">{s.description}</p></td><td>{s.durationMinutes} min<small>Antes: {s.bufferBeforeMinutes} · Depois: {s.bufferAfterMinutes} min</small></td><td>{money(s.priceCents)}<small>{s.depositPercent}% · {money(s.depositCents)}</small></td><td>{s.active?"Ativo":"Inativo"}</td><td><button className="secondary-button" disabled={busy||!operational} onClick={()=>{setEditing(s);setFormOpen(true);setError("");setMessage("");window.scrollTo({top:0,behavior:"smooth"});}}>Editar<span className="sr-only"> {s.name}</span></button></td></tr>)}</tbody></table></div>}
 <div className="platform-actions"><button className="secondary-button" disabled={busy||offset===0} onClick={()=>refresh(Math.max(0,offset-50))}>Anterior</button><span>Página {offset/50+1}</span><button className="secondary-button" disabled={busy||offset+50>=catalog.total||offset>=100000} onClick={()=>refresh(offset+50)}>Próxima</button></div></>}
 </section></section>;
}
