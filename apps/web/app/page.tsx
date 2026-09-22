import type { CSSProperties } from "react";
import Brand from "./components/Brand";

const benefits = [
  { symbol: "01", title: "Uma rotina mais leve", text: "Menos idas e vindas para combinar horários. Mais espaço para fazer o que você faz de melhor." },
  { symbol: "02", title: "Seu negócio, sua identidade", text: "Comece pelo seu perfil: nome, descrição e fuso horário em um só lugar, do seu jeito." },
  { symbol: "03", title: "Tranquilidade para começar", text: "Experimente o Premium/Top por 7 dias. Quando o teste terminar, suas configurações continuam preservadas." }
];
const week = ["Seg", "Ter", "Qua", "Qui", "Sex"];
const appointments = [
  { day: 0, top: 20, name: "Corte + finalização", time: "09:00 – 10:00", color: "teal" },
  { day: 1, top: 78, name: "Consulta inicial", time: "10:00 – 11:00", color: "purple" },
  { day: 2, top: 20, name: "Atendimento", time: "09:00 – 10:00", color: "teal" },
  { day: 3, top: 136, name: "Avaliação", time: "11:00 – 12:00", color: "amber" },
  { day: 4, top: 78, name: "Atendimento", time: "10:00 – 11:00", color: "teal" }
];

function Arrow() { return <span aria-hidden="true">↗</span>; }
function SchedulePreview() {
  return <figure className="product-preview" aria-label="Ilustração da proposta de agenda do Agendou, com horários fictícios">
    <div className="preview-glow" />
    <div className="desktop-preview">
      <div className="window-bar"><span className="window-dots"><i /><i /><i /></span><span>Seu espaço de trabalho</span><span aria-hidden="true">⌘</span></div>
      <div className="preview-layout">
        <aside className="preview-sidebar"><span className="mini-wordmark">agendou<span>.</span></span><span className="sidebar-caption">MEU NEGÓCIO</span><span className="mock-nav selected">▦ <span>Minha agenda</span></span><span className="mock-nav">◇ <span>Serviços</span></span><span className="mock-nav">♧ <span>Clientes</span></span><span className="mock-nav">◎ <span>Meu perfil</span></span><div className="mock-trial"><span className="status-dot" /> Premium/Top</div></aside>
        <div className="preview-content"><div className="preview-heading"><div><small>SEU DIA, ORGANIZADO</small><h3>Minha agenda</h3></div><span className="avatar">A</span></div><div className="calendar-toolbar"><strong>Uma semana de possibilidades</strong><span aria-hidden="true">‹ &nbsp; ›</span></div>
          <div className="calendar-week">{week.map((day, i) => <div className={i === 2 ? "today" : ""} key={day}>{day}<b>{21+i}</b></div>)}</div>
          <div className="calendar-body"><div className="calendar-lines" />{appointments.map((item, index) => <div className={`appointment ${item.color}`} key={index} style={{ "--day": item.day, top: item.top } as CSSProperties}><b>{item.name}</b><span>{item.time}</span></div>)}</div>
          <div className="calendar-bottom"><span className="status-dot" /> Cada atendimento tem o seu espaço.</div>
        </div>
      </div>
    </div>
    <div className="phone-preview"><div className="phone-notch" /><div className="phone-logo">agendou<span>.</span></div><div className="business-avatar">A</div><strong>Seu negócio</strong><small>Um tempo reservado para você.</small><div className="phone-service"><span>Atendimento</span><b>60 min</b></div><p>Escolha seu horário</p><div className="phone-times"><span>09:00</span><span className="chosen">10:00</span><span>11:00</span><span>14:00</span></div><div className="phone-confirm">Seu próximo cuidado começa aqui <span>↗</span></div></div>
    <figcaption>VISÃO DO PRODUTO · AGENDA ILUSTRATIVA</figcaption>
  </figure>;
}

export default function Home() {
  return <div className="landing">
    <a className="skip-link" href="#conteudo">Pular para o conteúdo</a>
    <header className="site-header"><nav className="site-nav container" aria-label="Principal"><Brand /><div className="nav-links"><a href="#como-funciona">Como funciona</a><a href="#vantagens">Por que Agendou?</a></div><div className="nav-actions"><a className="login-link" href="/entrar">Entrar</a><a className="primary-button compact" href="/cadastro">Começar grátis <Arrow /></a></div></nav></header>
    <main id="conteudo">
      <section className="hero container" aria-labelledby="hero-title"><div className="hero-copy"><span className="eyebrow-pill"><span className="status-dot" /> MAIS TEMPO PARA O QUE IMPORTA</span><h1 id="hero-title">Seu cliente agenda.<br /><span>Você cuida do<br className="desktop-break" /> seu negócio.</span></h1><p className="hero-description">Seu talento merece toda a sua atenção. Dê o primeiro passo para uma rotina de atendimentos mais simples, organizada e com a sua cara.</p><div className="hero-actions"><a className="primary-button" href="/cadastro">Começar meus 7 dias grátis <Arrow /></a><a className="secondary-button" href="#como-funciona">Conhecer o Agendou <span aria-hidden="true">↓</span></a></div><div className="hero-notes"><span><i>✓</i> Trial Premium/Top</span><span><i>✓</i> Sem cartão para começar</span></div></div><SchedulePreview /></section>
      <div className="audience-strip container"><span>FEITO PARA QUEM CUIDA DE PESSOAS</span><div><span>Beleza & estética</span><i /><span>Saúde & bem-estar</span><i /><span>Serviços & consultorias</span></div></div>
      <section className="benefits-section container" id="vantagens"><div className="section-heading"><p className="eyebrow">MENOS COMPLICAÇÃO. MAIS POSSIBILIDADES.</p><h2>Seu tempo vale muito.<br /><span>O do seu negócio também.</span></h2></div><div className="benefit-grid">{benefits.map(item => <article className="benefit-card" key={item.symbol}><span className="benefit-number">{item.symbol}<span aria-hidden="true">↗</span></span><h3>{item.title}</h3><p>{item.text}</p></article>)}</div></section>
      <section className="how-section container" id="como-funciona"><div className="how-visual"><div className="profile-illustration"><div className="profile-cover" /><div className="profile-mark">A<span>✓</span></div><p>UM ESPAÇO COM A SUA CARA</p><h3>O seu negócio.<br />A sua identidade.</h3><div className="profile-field"><small>NOME DO NEGÓCIO</small><span>Seu próximo capítulo</span></div><div className="profile-field"><small>SOBRE VOCÊ</small><span>Mostre o que torna seu trabalho especial.</span></div><span className="saved-label">✓ &nbsp; Tudo começa pelo seu perfil</span></div></div><div className="how-copy"><p className="eyebrow">SIMPLES DESDE O PRIMEIRO PASSO</p><h2>Comece pelo seu negócio.<br /><span>A gente cuida do caminho.</span></h2><ol className="steps"><li><span>1</span><div><h3>Crie sua conta</h3><p>Inicie seu teste Premium/Top de 7 dias e confirme seu email.</p></div></li><li><span>2</span><div><h3>Dê a sua cara ao perfil</h3><p>Adicione o nome, a descrição e o fuso do seu negócio.</p></div></li><li><span>3</span><div><h3>Tenha seu espaço de trabalho</h3><p>Acesse seu painel, acompanhe a assinatura e mantenha seu perfil atualizado.</p></div></li></ol><a className="text-link" href="/cadastro">Criar meu espaço <Arrow /></a></div></section>
      <section className="faq-section container" aria-labelledby="faq-title"><div><p className="eyebrow">VAMOS SIMPLIFICAR?</p><h2 id="faq-title">Antes de começar.</h2><p>Algumas respostas para dar o próximo passo com tranquilidade.</p></div><div className="faq-list"><details><summary>Como funciona o teste grátis?</summary><p>Ao criar sua conta, você inicia 7 dias no plano Premium/Top. O cadastro não exige cartão. Confirme seu email para entrar e configurar seu perfil.</p></details><details><summary>O que acontece depois dos 7 dias?</summary><p>Sem pagamento confirmado, as alterações operacionais ficam bloqueadas. Seu perfil e suas configurações são preservados para uma futura reativação.</p></details><details><summary>Já posso receber agendamentos?</summary><p>Nesta etapa, você já pode criar sua conta, configurar seu perfil e acompanhar sua assinatura. Agenda, reservas e conferência PIX estão em desenvolvimento. A agenda apresentada nesta página é ilustrativa.</p></details></div></section>
      <section className="closing-cta container"><div className="cta-orbit" aria-hidden="true" /><p className="eyebrow">SEU NEGÓCIO MERECE ESSE CUIDADO</p><h2>Mais organização.<br /><span>Mais espaço para você.</span></h2><p>Comece com 7 dias de Premium/Top por nossa conta.</p><a className="primary-button" href="/cadastro">Quero começar grátis <Arrow /></a></section>
    </main>
    <footer className="site-footer container"><Brand /><p>Seu cliente agenda. Você cuida do seu negócio.</p><div><a href="/entrar">Entrar</a><a href="/cadastro">Criar conta</a><span>© {new Date().getFullYear()} Agendou</span></div></footer>
  </div>;
}
