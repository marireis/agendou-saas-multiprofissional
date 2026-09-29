"use client";
import {useEffect,useRef,useState} from "react";
import {api} from "../lib/api";

type Payment={id:string;status:string;totalCents:number;depositCents:number;remainingAfterDepositCents:number;deadline:string;timezone:string;paymentAvailable:boolean;keyType?:string;pixKey?:string;recipientName?:string;instructions?:string};
const money=(value:number)=>new Intl.NumberFormat("pt-BR",{style:"currency",currency:"BRL"}).format(value/100);
export default function BookingPayment({slug,bookingId}:{slug:string;bookingId:string}){
 const [open,setOpen]=useState(false),[data,setData]=useState<Payment|null>(null),[error,setError]=useState(""),[copied,setCopied]=useState(""),[now,setNow]=useState(Date.now()),[busy,setBusy]=useState(false);
 const keyField=useRef<HTMLInputElement>(null);
 useEffect(()=>{
  if(!open)return;let alive=true;let running=false;
  async function refresh(){if(running)return;running=true;setBusy(true);try{const next=await api(`/public/${encodeURIComponent(slug)}/client/bookings/${bookingId}/payment`);if(alive){setData(next);setError("");setNow(Date.now());}}catch(e){if(alive){setData(null);setError(e instanceof Error?e.message:"Não foi possível consultar os dados.");}}finally{running=false;if(alive)setBusy(false);}}
  void refresh();const timer=setInterval(()=>void refresh(),15000),tick=setInterval(()=>setNow(Date.now()),1000);
  return()=>{alive=false;clearInterval(timer);clearInterval(tick);};
 },[open,slug,bookingId]);
 const expired=!!data&&(data.status==="EXPIRED"||now>=Date.parse(data.deadline));
 async function copy(){if(!data?.pixKey||expired||busy)return;setCopied("");try{await navigator.clipboard.writeText(data.pixKey);setCopied("Chave copiada. Confira os dados antes de qualquer transferência.");}catch{keyField.current?.focus();keyField.current?.select();setCopied("Selecionei a chave para você copiar manualmente.");}}
 return <section className="profile-panel" aria-label="Dados de pagamento PIX"><h3>Pagamento PIX</h3><p className="calendar-note">Prévia dos dados da reserva. O envio e a conferência de comprovantes ainda estão em preparação. Não realize transferência por esta etapa.</p>
 {!open?<button type="button" className="secondary-button" onClick={()=>setOpen(true)}>Ver dados PIX da reserva</button>:<>
 {busy&&!data&&<p role="status">Consultando dados…</p>}{error&&<p role="alert" className="form-error">{error}</p>}
 {data&&<><dl className="pix-amounts"><div><dt>Total do serviço</dt><dd>{money(data.totalCents)}</dd></div><div><dt>Entrada prevista</dt><dd>{money(data.depositCents)}</dd></div><div><dt>Saldo após pagar a entrada</dt><dd>{money(data.remainingAfterDepositCents)}</dd></div></dl><p>Nenhum recebimento foi confirmado pelo sistema.</p>
 {expired?<p role="status">Prazo encerrado. Não faça PIX para esta reserva; o horário foi liberado.</p>:<><p>Prazo da reserva: {new Intl.DateTimeFormat("pt-BR",{timeZone:data.timezone,dateStyle:"short",timeStyle:"short"}).format(new Date(data.deadline))} · {data.timezone} ({Math.max(0,Math.ceil((Date.parse(data.deadline)-now)/60000))} min restantes)</p><p>Recebedor: <strong>{data.recipientName}</strong></p><label>Chave PIX · {({EMAIL:"Email",PHONE:"Telefone",CPF:"CPF",CNPJ:"CNPJ",RANDOM:"Aleatória"} as Record<string,string>)[data.keyType||""]||data.keyType}<input ref={keyField} value={data.pixKey||""} readOnly autoComplete="off"/></label><button type="button" className="secondary-button" disabled={busy} onClick={copy}>Copiar chave PIX</button>{copied&&<p role="status">{copied}</p>}{data.instructions&&<><h4>Instruções registradas pelo profissional</h4><p className="public-description">{data.instructions}</p></>}</>}
 </>}
 <p><button type="button" className="secondary-button" onClick={()=>{setOpen(false);setData(null);setCopied("");setError("");}}>Ocultar dados</button></p>
 </>}
 </section>;
}
