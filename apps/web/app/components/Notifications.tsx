"use client";
import {useEffect,useRef,useState} from "react";
import Link from "next/link";
import {api} from "../lib/api";

type Notice={id:string;bookingId:string;type:string;at:string;serviceName:string;start:string;timezone:string;read:boolean};
type Feed={items:Notice[];unreadCount:number;nextCursor:string};
const titles:Record<string,string>={REQUESTED:"Nova solicitação de agendamento",IN_REVIEW:"Comprovante recebido",CONFIRMED:"Atendimento confirmado",RESCHEDULED:"Atendimento reagendado",EXPIRED:"Prazo da reserva encerrado",CANCELED:"Atendimento cancelado",COMPLETED:"Atendimento concluído",NO_SHOW:"Não compareceu",PAYMENT_RECORDED:"Recebimento registrado",REFUND_RECORDED:"Devolução registrada"};

export default function Notifications(){
 const [feed,setFeed]=useState<Feed|null>(null),[unreadOnly,setUnreadOnly]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState("");
 const request=useRef(0);
 async function load(before="",append=false){
  const version=++request.current;setBusy(true);setError("");
  try{const result:Feed=await api(`/admin/notifications?unreadOnly=${unreadOnly}${before?`&before=${before}`:""}`);if(version===request.current)setFeed(old=>({...result,items:append&&old?[...old.items,...result.items]:result.items}));}
  catch(e){if(version===request.current)setError(e instanceof Error?e.message:"Não foi possível carregar os avisos.");}
  finally{if(version===request.current)setBusy(false);}
 }
 useEffect(()=>{setFeed(null);void load();return()=>{request.current++;};},[unreadOnly]);
 async function markRead(id:string){
  setBusy(true);setError("");try{await api(`/admin/notifications/${id}/read`,"POST");await load();}
  catch(e){setError(e instanceof Error?e.message:"Não foi possível marcar como lida.");setBusy(false);}
 }
 return <><p className="eyebrow">ACOMPANHE SEUS ATENDIMENTOS</p><h1>Notificações</h1><p>Novas solicitações e mudanças nas reservas, reunidas aqui. A leitura é individual para cada administrador.</p>
 <section className="profile-panel"><div className="profile-actions"><label><input type="checkbox" checked={unreadOnly} disabled={busy} onChange={e=>setUnreadOnly(e.target.checked)}/> Somente não lidas</label><button className="secondary-button" disabled={busy} onClick={()=>load()}>Atualizar</button><span role="status">{busy?"Carregando…":feed?`${feed.unreadCount} não lida(s)`:""}</span></div>
 {error&&<p className="form-error" role="alert">{error}</p>}
 {feed&&!busy&&!feed.items.length&&<p>{unreadOnly?"Tudo em dia! Nenhuma notificação não lida.":"As movimentações dos seus agendamentos aparecerão aqui."}</p>}
 <ul className="notification-list">{feed?.items.map(item=><li key={item.id} className={`notification-item ${item.read?"":"notification-unread"}`}><div><small>{item.read?"Lida":"Não lida"} · {new Date(item.at).toLocaleString("pt-BR")}</small><h2>{titles[item.type]||"Atualização do atendimento"}</h2><p>{item.serviceName}</p><p>Horário atual: {new Intl.DateTimeFormat("pt-BR",{timeZone:item.timezone,dateStyle:"short",timeStyle:"short"}).format(new Date(item.start))}</p><small>Protocolo: {item.bookingId}</small></div><div className="profile-actions"><Link className="secondary-button" href={`/painel/conferencia?reserva=${item.bookingId}`}>Ver atendimento</Link>{!item.read&&<button className="secondary-button" disabled={busy} onClick={()=>markRead(item.id)}>Marcar como lida</button>}</div></li>)}</ul>
 {feed?.nextCursor&&<button className="secondary-button" disabled={busy} onClick={()=>load(feed.nextCursor,true)}>Carregar anteriores</button>}
 </section><p>Os avisos são atualizados ao abrir esta aba ou clicar em Atualizar. O cliente acompanha sua reserva em Meus agendamentos com o código privado. Não há envio automático por WhatsApp/SMS nesta etapa.</p></>;
}
