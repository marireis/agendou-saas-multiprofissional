"use client";
import {useEffect,useRef,useState} from "react";
import Link from "next/link";
import {api} from "../lib/api";
type Publication={published:boolean;path:string};
export default function Sharing({name}:{name:string}){
 const [publication,setPublication]=useState<Publication|null>(null),[origin,setOrigin]=useState(""),[busy,setBusy]=useState(true),[error,setError]=useState(""),[feedback,setFeedback]=useState(""),[message,setMessage]=useState<string|null>(null);
 const generation=useRef(0),linkField=useRef<HTMLInputElement>(null),messageField=useRef<HTMLTextAreaElement>(null);
 const link=publication?`${origin}${publication.path}`:"";
 const suggestion=`Olá! Conheça ${name}, confira nossos serviços e encontre nossos contatos:\n\n${link}`;
 const text=message??suggestion;
 const local=origin?new URL(origin).hostname==="localhost"||new URL(origin).hostname==="127.0.0.1"||new URL(origin).hostname==="[::1]":false;
 const ready=!!publication?.published&&!busy&&!error;
 async function load(){const id=++generation.current;setBusy(true);setError("");try{const state:Publication=await api("/admin/publication");if(id===generation.current)setPublication(state);}catch(e){if(id===generation.current){setPublication(null);setError(e instanceof Error?e.message:"Não foi possível verificar a publicação.");}}finally{if(id===generation.current)setBusy(false);}}
 useEffect(()=>{setOrigin(window.location.origin);void load();const refresh=()=>{void load();};window.addEventListener("focus",refresh);return()=>{generation.current++;window.removeEventListener("focus",refresh);};},[]);
 async function copy(value:string,kind:"link"|"message"){
  if(!ready)return;setFeedback("");
  try{await navigator.clipboard.writeText(value);setFeedback(kind==="link"?"Link copiado!":"Mensagem copiada!");}
  catch{const field=kind==="link"?linkField.current:messageField.current;field?.focus();field?.select();setFeedback("A cópia automática não foi permitida. O texto está selecionado: use Copiar ou Ctrl+C.");}
 }
 return <><h1>Compartilhar sua página</h1><p>Convide seus clientes a conhecer seus serviços e encontrar seus contatos.</p>{error&&<p role="alert" className="form-error">{error}</p>}<button type="button" className="secondary-button" disabled={busy} onClick={()=>{setFeedback("");void load();}}>{busy?"Verificando publicação…":"Atualizar publicação"}</button>{publication&&!publication.published&&<section className="profile-panel"><h2>Publique sua página primeiro</h2><p>O compartilhamento fica disponível depois que sua página estiver publicada.</p><Link className="primary-button" href="/painel/publicacao">Ir para Publicação</Link></section>}{publication?.published&&<><section className="profile-panel"><h2>Link da sua página</h2>{local&&<p role="note">Este é um endereço local de teste: ele funciona neste computador. Para seus clientes acessarem, o Agendou precisa estar hospedado em um endereço público.</p>}<label>Link completo<input ref={linkField} value={link} readOnly onFocus={e=>e.target.select()}/></label><div className="profile-actions"><button type="button" className="primary-button" disabled={!ready} onClick={()=>void copy(link,"link")}>Copiar link</button><a className="secondary-button" href={publication.path} target="_blank" rel="noopener noreferrer">Ver página pública</a></div></section><section className="profile-panel"><h2>Mensagem para WhatsApp</h2><p>Edite o convite abaixo. No WhatsApp, você escolhe o contato e confirma o envio.</p><label>Seu convite<textarea ref={messageField} rows={7} maxLength={2000} value={text} onChange={e=>{setMessage(e.target.value);setFeedback("");}}/></label><small>O texto editado vale enquanto esta tela estiver aberta. O agendamento online ainda está em preparação.</small><div className="profile-actions"><button type="button" className="secondary-button" onClick={()=>{setMessage(null);setFeedback("");}}>Restaurar sugestão</button><button type="button" className="secondary-button" disabled={!ready||!text.trim()} onClick={()=>void copy(text,"message")}>Copiar mensagem</button>{ready&&text.trim()?<a className="primary-button" href={`https://wa.me/?text=${encodeURIComponent(text)}`} target="_blank" rel="noopener noreferrer">Abrir no WhatsApp</a>:<button className="primary-button" disabled>Abrir no WhatsApp</button>}</div></section></>}{feedback&&<p role="status">{feedback}</p>}</>;
}
