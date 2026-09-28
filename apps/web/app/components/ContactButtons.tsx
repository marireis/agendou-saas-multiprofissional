function whatsappNumber(phone:string){
 const digits=phone.replace(/\D/g,"");
 if(phone.trim().startsWith("+"))return /^[1-9]\d{7,14}$/.test(digits)?digits:null;
 if(/^55\d{10,11}$/.test(digits))return digits;
 if(/^[1-9]\d{9,10}$/.test(digits))return `55${digits}`;
 return null;
}
export default function ContactButtons({email,phone}:{email:string;phone:string}){
 const number=whatsappNumber(phone);
 return <div className="contact-buttons">
  {email&&<a className="contact-button" href={`mailto:${encodeURIComponent(email)}`}><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><rect x="3" y="5" width="18" height="14" rx="3"/><path d="m3 7 9 6 9-6"/></svg><span>Enviar email</span></a>}
  {number&&<a className="contact-button whatsapp" href={`https://wa.me/${number}`} target="_blank" rel="noopener noreferrer"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M20.5 11.5a9 9 0 0 1-13.4 7.9L3 21l1.4-4.2a9 9 0 1 1 16.1-5.3Z"/><path d="m8 7 2 3-1.2 1.1a9 9 0 0 0 4.1 4.1L14 14l3 2c-.4 1.5-1.4 2-2.7 1.5C10.4 16.2 7.8 13.6 6.5 9.7 6 8.4 6.5 7.4 8 7Z"/></svg><span>Conversar no WhatsApp</span></a>}
  {phone&&!number&&<a className="contact-button" href={`tel:${phone.replace(/[^+0-9]/g,"")}`}>Ligar para {phone}</a>}
 </div>;
}
