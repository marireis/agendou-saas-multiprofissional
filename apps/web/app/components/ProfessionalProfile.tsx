"use client";

import { useState, type FormEvent } from "react";
import { api } from "../lib/api";

export type Profile = {
  name: string; slug: string; description: string; timezone: string;
  contactEmail: string; contactPhone: string; serviceMode: string; location: string;
  logoVersion: string | null; profileProgress: number; profileComplete: boolean; missingFields: string[];
};
const modes: Record<string,string> = { UNSET: "Selecione a modalidade", IN_PERSON: "Presencial", ONLINE: "Online", HYBRID: "Presencial e online" };
const zones = ["America/Sao_Paulo","America/Manaus","America/Cuiaba","America/Rio_Branco","America/Noronha","America/Bahia","America/Fortaleza","America/Belem","UTC"];

export default function ProfessionalProfile({ profile, operational, onChange, onBlocked }: {
  profile: Profile; operational: boolean; onChange: (value: Profile) => void; onBlocked: () => Promise<void>;
}) {
  const [draft,setDraft]=useState(profile);
  const [busy,setBusy]=useState(false);
  const [message,setMessage]=useState("");
  const [error,setError]=useState("");
  const [file,setFile]=useState<File | null>(null);
  const [fileKey,setFileKey]=useState(0);
  function field(key: keyof Profile,value: string) {setDraft(previous=>({...previous,[key]:value}));}
  async function action(work: ()=>Promise<void>) {
    if(busy)return;setBusy(true);setError("");setMessage("");
    try {await work();}catch(e) {setError(e instanceof Error?e.message:"Não foi possível salvar.");await onBlocked();}finally{setBusy(false);}
  }
  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();void action(async()=>{
      const saved: Profile=await api("/admin/profile","PATCH",{
        name:draft.name,description:draft.description,timezone:draft.timezone,contactEmail:draft.contactEmail,
        contactPhone:draft.contactPhone,serviceMode:draft.serviceMode,location:draft.location,
      });
      setDraft(saved);onChange(saved);setMessage("Perfil salvo. Suas informações estão preservadas.");
    });
  }
  function upload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();if(!file)return;
    if(file.size>2*1024*1024) {setError("Escolha uma imagem de até 2 MB.");return;}
    void action(async()=>{onChange(await api("/admin/profile/logo","PUT",file));setFile(null);setFileKey(n=>n+1);setMessage("Logomarca atualizada.");});
  }
  function removeLogo() {void action(async()=>{onChange(await api("/admin/profile/logo","DELETE"));setMessage("Logomarca removida.");});}
  const logoUrl=profile.logoVersion?`/api/v1/admin/profile/logo?v=${profile.logoVersion}`:null;
  const local=draft.serviceMode==="IN_PERSON" || draft.serviceMode==="HYBRID";
  return <>
    <section className="profile-progress" aria-labelledby="profile-progress-title"><div><p className="eyebrow">SEU PERFIL PROFISSIONAL</p><h2 id="profile-progress-title">{profile.profileComplete?"Informações do perfil completas":"Vamos apresentar seu negócio"}</h2><p>Preencha no seu ritmo. O progresso considera as informações salvas.</p></div><strong>{profile.profileProgress}%</strong><progress value={profile.profileProgress} max={100} aria-label="Preenchimento do perfil" />
      {profile.missingFields.length>0 && <p>Falta preencher: {profile.missingFields.join(" · ")}.</p>}
      <small>Perfil completo não publica a agenda. A publicação será liberada após configurar serviços, política e disponibilidade.</small>
    </section>
    {error && <p className="form-error" role="alert">{error}</p>}{message && <p className="profile-feedback" role="status">{message}</p>}
    <div className="profile-layout"><div>
      <section className="profile-panel"><h2>Identidade do negócio</h2><p>Estas informações vão apresentar seu atendimento aos clientes.</p>
        <form onSubmit={save} aria-busy={busy}><fieldset disabled={busy || !operational}>
          <label>Nome do negócio<input value={draft.name} onChange={e=>field("name",e.target.value)} required maxLength={100} autoComplete="organization" /></label>
          <label>Endereço reservado<input value={`/a/${profile.slug}`} readOnly aria-describedby="slug-note" /></label><small id="slug-note">Esse endereço foi escolhido no cadastro. Sua página ainda não está publicada.</small>
          <label>Descrição<textarea value={draft.description} onChange={e=>field("description",e.target.value)} maxLength={2000} rows={4} placeholder="Conte o que você faz e como atende seus clientes." /></label>
          <div className="profile-fields"><label>Email de contato<input type="email" value={draft.contactEmail} onChange={e=>field("contactEmail",e.target.value)} maxLength={254} autoComplete="email" /></label><label>Telefone de contato<input type="tel" value={draft.contactPhone} onChange={e=>field("contactPhone",e.target.value)} maxLength={24} placeholder="+55 11 99999-9999" autoComplete="tel" /></label></div>
          <small>Informe pelo menos um contato comercial para completar o perfil. Ele será exibido quando você publicar sua página.</small>
          <div className="profile-fields"><label>Modalidade de atendimento<select value={draft.serviceMode} onChange={e=>field("serviceMode",e.target.value)}>{Object.entries(modes).map(([key,label])=><option key={key} value={key}>{label}</option>)}</select></label>
          <label>Fuso do negócio<input list="profile-timezones" value={draft.timezone} onChange={e=>field("timezone",e.target.value)} required maxLength={100} /><datalist id="profile-timezones">{zones.map(zone=><option key={zone} value={zone}/>)}</datalist></label></div>
          {local && <label>Local de atendimento<textarea value={draft.location} onChange={e=>field("location",e.target.value)} maxLength={500} rows={3} placeholder="Endereço ou orientações de chegada" autoComplete="street-address" /></label>}
          {draft.serviceMode==="ONLINE" && <p>O atendimento será apresentado como online. Não inclua links privados de reunião no perfil.</p>}
          <div className="profile-actions"><button className="primary-button">{busy?"Salvando…":"Salvar perfil"}</button><button type="button" className="secondary-button" onClick={()=>setDraft(profile)}>Desfazer edição</button></div>
        </fieldset></form>
      </section>
      <section className="profile-panel"><h2>Sua logomarca</h2><p>Opcional. PNG ou JPEG de até 2 MB e 4096 pixels por lado. A imagem será ajustada para até 512 pixels, sem cortar.</p>
        <form onSubmit={upload} aria-busy={busy}><fieldset disabled={busy || !operational}><label>Arquivo da logomarca<input key={fileKey} type="file" accept="image/png,image/jpeg" onChange={e=>{setFile(e.target.files?.[0] || null);setError("");}} /></label><div className="profile-actions"><button className="primary-button" disabled={!file}>Salvar logomarca</button>{logoUrl && <button type="button" className="secondary-button" onClick={removeLogo}>Remover logomarca</button>}</div></fieldset></form>
      </section>
    </div><aside className="profile-preview"><p className="eyebrow">PRÉVIA DO PERFIL</p><div className="profile-preview-card">
      {logoUrl?<img className="professional-logo" src={logoUrl} alt={`Logomarca de ${profile.name}`} width={112} height={112}/>:<div className="professional-logo logo-placeholder" aria-hidden="true">{draft.name.trim().slice(0,1).toUpperCase() || "A"}</div>}
      <h2>{draft.name || "Nome do seu negócio"}</h2><p className="profile-description">{draft.description || "Sua descrição aparecerá aqui."}</p>
      {draft.serviceMode!=="UNSET" && <span className="profile-mode">{modes[draft.serviceMode]}</span>}
      {local && draft.location && <p className="profile-description">{draft.location}</p>}
      {(draft.contactEmail || draft.contactPhone) && <div className="profile-contact"><strong>Contato</strong>{draft.contactEmail && <p>{draft.contactEmail}</p>}{draft.contactPhone && <p>{draft.contactPhone}</p>}</div>}
    </div><small>Prévia da edição. Salve para manter as alterações. Não é uma página pública de agendamento.</small></aside></div>
  </>;
}
