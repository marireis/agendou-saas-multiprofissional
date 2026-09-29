import Brand from "./Brand";
import Link from "next/link";
import BusinessLocation from "./BusinessLocation";
import ContactButtons from "./ContactButtons";
export type PublicPageData={name:string;slug:string;description:string;timezone:string;contactEmail:string;contactPhone:string;serviceMode:string;location:string;hasLogo:boolean;bookingAvailable:boolean;services:{id:string;name:string;description:string;durationMinutes:number;priceCents:number;depositCents:number}[]};
const money=(cents:number)=>new Intl.NumberFormat("pt-BR",{style:"currency",currency:"BRL"}).format(cents/100);
export default function PublicProfessionalPage({page,preview=false}:{page:PublicPageData;preview?:boolean}){
 return <main className="public-professional"><header><Brand/><span>{preview?"PRÉVIA PRIVADA":"ATENDIMENTO COM HORA MARCADA"}</span></header><section className="public-profile-hero">
 {page.hasLogo&&<img className="public-business-logo" src={preview?"/api/v1/admin/profile/logo":`/api/v1/public/pages/${encodeURIComponent(page.slug)}/logo`} alt={`Logomarca de ${page.name}`}/>}
 <p className="eyebrow">SEU PRÓXIMO MOMENTO DE CUIDADO</p><h1>{page.name}</h1><p className="public-description">{page.description}</p>
 <div className="public-details"><span>{({ONLINE:"Atendimento online",IN_PERSON:"Atendimento presencial",HYBRID:"Atendimento presencial e online"} as Record<string,string>)[page.serviceMode]||"Modalidade a definir"}</span><span>Fuso: {page.timezone}</span></div>
 </section><section className="public-booking-status" aria-label="Disponibilidade"><strong>Encontre um horário para você</strong><p>Escolha serviço, data e horário, verifique seu email e revise antes de solicitar uma reserva temporária. O pagamento ainda está em preparação.</p>{preview?<p>A consulta de horários aparece na página publicada.</p>:<div className="profile-actions"><Link className="primary-button" href={`/a/${page.slug}/agendar`}>Consultar serviços e horários</Link><Link className="secondary-button" href={`/a/${page.slug}/meus-agendamentos`}>Meus agendamentos</Link></div>}</section>
 <section aria-labelledby="public-services"><h2 id="public-services">Escolha seu cuidado</h2><div className="public-service-grid">{page.services.map(service=><article key={service.id}><span>{service.durationMinutes} min</span><h3>{service.name}</h3><p className="public-description">{service.description}</p><strong>{money(service.priceCents)}</strong><small>Entrada de {money(service.depositCents)}</small></article>)}</div>{!page.services.length&&<p>Nenhum serviço disponível no momento.</p>}</section>
 {(page.serviceMode==="IN_PERSON"||page.serviceMode==="HYBRID")&&page.location.trim()&&<section className="public-contact" aria-labelledby="location-title"><h2 id="location-title">Onde estamos</h2><BusinessLocation address={page.location}/></section>}
 <section className="public-contact"><h2>Vamos conversar?</h2><ContactButtons email={page.contactEmail} phone={page.contactPhone}/></section><footer>Agendou · Seu cliente agenda. Você cuida do seu negócio.</footer></main>;
}
