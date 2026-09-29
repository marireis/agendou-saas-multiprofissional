"use client";
import {useCallback,useEffect,useRef,useState} from "react";
import Link from "next/link";
import BookingPayment from "./BookingPayment";
import {api,ApiError} from "../lib/api";

type Booking={id:string;status:"AWAITING_PAYMENT"|"EXPIRED";start:string;end:string;expiresAt:string;timezone:string;priceCents:number;depositCents:number;serviceName:string};
type Detail=Booking&{businessName:string;serviceMode:string;location:string;cancellationPolicy:string;events:{type:string;at:string}[]};
type Page={items:Booking[];hasMore:boolean;offset:number;email:string};
const money=(value:number)=>new Intl.NumberFormat("pt-BR",{style:"currency",currency:"BRL"}).format(value/100);
const date=(value:string,zone:string)=>new Intl.DateTimeFormat("pt-BR",{dateStyle:"short",timeStyle:"short",timeZone:zone}).format(new Date(value));
export default function CustomerAppointments({slug}:{slug:string}){
 const prefix=`/public/${encodeURIComponent(slug)}/client`;
 const [page,setPage]=useState<Page|null>(null),[detail,setDetail]=useState<Detail|null>(null),[offset,setOffset]=useState(0),[auth,setAuth]=useState(false),[busy,setBusy]=useState(false),[loading,setLoading]=useState(true),[error,setError]=useState(""),[message,setMessage]=useState(""),[token,setToken]=useState<{token:string;email:string}|null>(null),[cooldown,setCooldown]=useState(0),[now,setNow]=useState(Date.now());
 const initialized=useRef(false),generation=useRef(0),selected=useRef<string|null>(null);
 const refresh=useCallback(async()=>{
  const request=++generation.current;setLoading(true);
  try{
   const result:Page=await api(`${prefix}/bookings?offset=${offset}`);
   const current=selected.current;const full:Detail|null=current?await api(`${prefix}/bookings/${current}`):null;
   if(request!==generation.current)return;
   setPage(result);setDetail(full);setAuth(true);setError("");setNow(Date.now());
  }catch(e){if(request!==generation.current)return;
   if(e instanceof ApiError&&e.status===401){setAuth(false);setPage(null);setDetail(null);selected.current=null;}
   else setError(e instanceof Error?e.message:"Não foi possível consultar seus agendamentos.");
  }finally{if(request===generation.current)setLoading(false);}
 },[prefix,offset]);
 useEffect(()=>{
  if(!initialized.current){initialized.current=true;const fragment=new URLSearchParams(window.location.hash.slice(1));const secret=fragment.get("token"),email=fragment.get("email");if(secret){setToken({token:secret,email:email||""});window.history.replaceState(null,"",window.location.pathname);setLoading(false);return;}}
  void refresh();return()=>{generation.current++;};
 },[refresh]);
 useEffect(()=>{if(!auth||token)return;const timer=window.setInterval(()=>void refresh(),30000);const tick=window.setInterval(()=>setNow(Date.now()),1000);return()=>{clearInterval(timer);clearInterval(tick);};},[auth,token,refresh]);
 useEffect(()=>{if(!cooldown)return;const timer=setTimeout(()=>setCooldown(v=>v-1),1000);return()=>clearTimeout(timer);},[cooldown]);
 async function requestLink(event:React.FormEvent<HTMLFormElement>){event.preventDefault();if(busy||cooldown)return;setBusy(true);setError("");setMessage("");const form=new FormData(event.currentTarget);
  try{const result=await api(`${prefix}/access-links`,"POST",{email:form.get("email")});setMessage(result.message);setCooldown(60);}catch(e){setError(e instanceof Error?e.message:"Não foi possível enviar o link.");if(e instanceof ApiError)setCooldown(e.retryAfter);}finally{setBusy(false);}
 }
 async function verify(){if(!token||busy)return;setBusy(true);setError("");generation.current++;selected.current=null;setPage(null);setDetail(null);
  try{await api(`${prefix}/access-links/consume`,"POST",token);setToken(null);setOffset(0);await refresh();}catch(e){setError(e instanceof Error?e.message:"Não foi possível verificar o link.");}finally{setBusy(false);}
 }
 async function logout(){setBusy(true);setError("");generation.current++;
  try{await api(`${prefix}/logout`,"POST");setAuth(false);setPage(null);setDetail(null);selected.current=null;setOffset(0);setMessage("");}catch(e){setError(e instanceof Error?e.message:"Não foi possível sair.");}finally{setBusy(false);}
 }
 function open(id:string|null){selected.current=id;setDetail(null);void refresh();}
 const expired=(b:Booking)=>b.status==="EXPIRED"||now>=Date.parse(b.expiresAt);
 return <main className="professional-content customer-appointments"><Link href={`/a/${encodeURIComponent(slug)}`}>← Página do profissional</Link><p className="eyebrow">SEU ESPAÇO</p><h1>Meus agendamentos</h1><p>Acompanhe suas solicitações e consulte os detalhes do atendimento.</p>
 {error&&<p role="alert" className="form-error">{error}</p>}
 {token?<section className="profile-panel"><h2>Confirme seu acesso</h2><p>O link é de uso único e vale por 15 minutos. Confirme para consultar seus agendamentos.</p><button className="primary-button" disabled={busy} onClick={verify}>Acessar meus agendamentos</button><p><button className="secondary-button" disabled={busy} onClick={()=>{setToken(null);setError("");void refresh();}}>Solicitar outro link</button></p></section>:!auth?<section className="profile-panel"><h2>Acesse com seu email</h2><p>Use o mesmo email informado ao reservar com este profissional. Enviaremos um link de acesso, sem senha.</p>{loading?<p role="status">Consultando acesso…</p>:<form onSubmit={requestLink}><label>Email<input name="email" type="email" autoComplete="email" required maxLength={254}/></label><button className="primary-button" disabled={busy||cooldown>0}>{cooldown?`Aguarde ${cooldown}s`:busy?"Enviando…":"Receber link de acesso"}</button></form>}{message&&<p role="status">{message}</p>}</section>:<>
 <div className="profile-actions"><span>{page?.email}</span><button className="secondary-button" disabled={busy||loading} onClick={()=>void refresh()}>Atualizar</button><button className="secondary-button" disabled={busy||loading} onClick={logout}>Sair deste acesso</button></div><p className="calendar-note">Acesso por uma hora. As solicitações temporárias ainda não confirmam atendimento. Pagamento e envio de comprovante ainda não estão habilitados.</p>
 {loading&&<p role="status">Atualizando agendamentos…</p>}
 {detail?<section className="profile-panel"><button className="secondary-button" onClick={()=>open(null)}>← Todos os agendamentos</button><h2>{detail.serviceName}</h2><p>{detail.businessName}</p><strong>{expired(detail)?"Solicitação expirada":"Solicitação pendente"}</strong><p>{date(detail.start,detail.timezone)} até {date(detail.end,detail.timezone)} · {detail.timezone}</p><p>{expired(detail)?"O prazo terminou e o horário foi liberado.":`Prazo restante: ${Math.max(0,Math.ceil((Date.parse(detail.expiresAt)-now)/60000))} minuto(s). Expira em ${date(detail.expiresAt,detail.timezone)}.`}</p><p>Total: {money(detail.priceCents)} · Entrada prevista: {money(detail.depositCents)}</p><p>Protocolo: <code className="booking-protocol">{detail.id}</code></p><p>Modalidade: {detail.serviceMode==="ONLINE"?"Online":detail.serviceMode==="HYBRID"?"Presencial e online":"Presencial"}</p>{detail.serviceMode!=="ONLINE"&&detail.location&&<p>Local informado na reserva: {detail.location}</p>}<h3>Política de cancelamento aceita</h3><p className="public-description">{detail.cancellationPolicy}</p><BookingPayment key={detail.id} slug={slug} bookingId={detail.id}/><h3>Histórico</h3><ul>{detail.events.map((event,i)=><li key={`${event.type}-${i}`}>{event.type==="REQUESTED"?"Solicitação registrada":event.type==="EXPIRED"?"Prazo expirado":event.type} · {date(event.at,detail.timezone)}</li>)}</ul></section>:<>
 {!page?.items.length&&!loading&&<section className="profile-panel"><h2>Nenhum agendamento por aqui</h2><p>As reservas feitas com este email aparecerão nesta área.</p></section>}
 <div className="appointment-list">{page?.items.map(item=><article className="profile-panel" key={item.id}><span className={`appointment-state ${expired(item)?"is-expired":""}`}>{expired(item)?"Expirada":"Pendente"}</span><h2>{item.serviceName}</h2><p>{date(item.start,item.timezone)} · {item.timezone}</p><p>{money(item.priceCents)} · {expired(item)?"Horário liberado":`${Math.max(0,Math.ceil((Date.parse(item.expiresAt)-now)/60000))} min para expirar`}</p><button className="secondary-button" disabled={loading} onClick={()=>open(item.id)} aria-label={`Ver detalhes de ${item.serviceName} em ${date(item.start,item.timezone)}`}>Ver detalhes</button></article>)}</div>
 <nav className="profile-actions" aria-label="Páginas de agendamentos"><button disabled={loading||offset===0} onClick={()=>setOffset(v=>Math.max(0,v-20))}>Anterior</button><span>Página {Math.floor(offset/20)+1}</span><button disabled={loading||!page?.hasMore||offset>=10000} onClick={()=>setOffset(v=>v+20)}>Próxima</button></nav>
 </>}
 </>}
 </main>;
}
