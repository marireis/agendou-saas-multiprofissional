"use client";
import {useEffect,useState} from "react";
import {api} from "../lib/api";
import PublicProfessionalPage,{type PublicPageData} from "./PublicProfessionalPage";
type Status={published:boolean;canPublish:boolean;missingRequirements:string[];path:string};
export default function Publication(){
 const [status,setStatus]=useState<Status|null>(null),[page,setPage]=useState<PublicPageData|null>(null),[error,setError]=useState(""),[busy,setBusy]=useState(false);
 async function load(){setBusy(true);setError("");try{const [state,profile]=await Promise.all([api("/admin/publication"),api("/admin/profile")]);const services=[];let offset=0;for(;;){const result=await api(`/admin/services?offset=${offset}`);services.push(...result.items.filter((s:{active:boolean})=>s.active));offset+=result.items.length;if(offset>=result.total||!result.items.length)break;}setStatus(state);setPage({...profile,hasLogo:!!profile.logoVersion,bookingAvailable:false,services});}catch(e){setError(e instanceof Error?e.message:"Não foi possível carregar.");}finally{setBusy(false);}}
 useEffect(()=>{void load();},[]);
 async function unpublish(){setBusy(true);setError("");try{await api("/admin/publication","DELETE");await load();}catch(e){setError(e instanceof Error?e.message:"Não foi possível retirar a página.");}finally{setBusy(false);}}
 return <><h1>Publicação da página</h1><p>Confira como seu negócio será apresentado aos clientes.</p>{error&&<p role="alert" className="form-error">{error}</p>}<button className="secondary-button" disabled={busy} onClick={load}>{busy?"Carregando…":"Atualizar prévia"}</button>{status&&<section className="profile-panel"><h2>{status.published?"Página publicada":"Sua página está em preparação"}</h2><p>Endereço reservado: <code>{status.path}</code></p><p>Para liberar a publicação:</p><ul>{status.missingRequirements.map(item=><li key={item}>{item}</li>)}</ul><p>Esta prévia é privada. Seus dados só serão exibidos no endereço público após a publicação.</p><button className="primary-button" disabled>Publicação aguardando requisitos</button>{status.published&&<button className="secondary-button" disabled={busy} onClick={unpublish}>Retirar página do ar</button>}</section>}{page&&<PublicProfessionalPage page={page} preview/>}</>;
}
