const setupSteps = [
  "Criar conta e iniciar trial Premium/Top",
  "Configurar perfil, servicos e PIX",
  "Publicar agenda e receber reservas",
  "Conferir PIX manualmente e confirmar"
];

const metrics = [
  { label: "Trial", value: "7 dias" },
  { label: "Plano", value: "Premium/Top" },
  { label: "Entrada", value: "50%+" }
];

export default function Home() {
  return (
    <main className="page-shell">
      <section className="hero" aria-labelledby="hero-title">
        <nav className="topbar" aria-label="Principal">
          <div className="brand-mark">agendou</div>
          <a href="/entrar" className="nav-action">Entrar</a>
        </nav>

        <div className="hero-grid">
          <div className="hero-copy">
            <p className="eyebrow">Agenda, pagamento e confirmacao em um fluxo so</p>
            <h1 id="hero-title">Publique sua agenda e receba reservas com PIX conferido.</h1>
            <p className="hero-text">
              O MVP do Agendou inicia profissionais no plano Premium/Top por 7 dias. Se o pagamento nao for confirmado ao final do trial, novas reservas sao bloqueadas sem apagar a pagina configurada.
            </p>
            <div className="hero-actions">
              <a className="primary-button" href="/cadastro">Criar minha conta</a>
              <a className="secondary-button" href="#operacao">Ver operacao</a>
            </div>
          </div>

          <aside className="status-panel" id="trial" aria-label="Status do trial">
            <div className="panel-header">
              <span>Status da assinatura</span>
              <strong>Premium/Top</strong>
            </div>
            <div className="trial-ring">
              <span>7</span>
              <small>dias gratis</small>
            </div>
            <p>
              Trial exclusivo do Premium/Top. O pagamento reativa o tenant preservando pagina, servicos, agenda e historico.
            </p>
          </aside>
        </div>
      </section>

      <section className="metrics" aria-label="Resumo do MVP">
        {metrics.map((metric) => (
          <div key={metric.label} className="metric-card">
            <span>{metric.label}</span>
            <strong>{metric.value}</strong>
          </div>
        ))}
      </section>

      <section className="workflow" id="onboarding" aria-labelledby="workflow-title">
        <div>
          <p className="eyebrow">Primeira fatia implementada</p>
          <h2 id="workflow-title">Base pronta para evoluir em fatias verticais.</h2>
        </div>
        <ol>
          {setupSteps.map((step) => (
            <li key={step}>{step}</li>
          ))}
        </ol>
      </section>

      <section className="operation" id="operacao" aria-label="Operacao do MVP">
        <div className="operation-card">
          <h2>Bloqueio sem perda de configuracao</h2>
          <p>
            Ao vencer o trial sem pagamento, o tenant entra em TRIAL_EXPIRED_BLOCKED. O usuario ainda encontra o caminho de pagamento e, ao regularizar, volta com a pagina ja configurada.
          </p>
        </div>
        <div className="operation-card accent">
          <h2>PIX manual no MVP</h2>
          <p>
            O cliente envia comprovante, mas a reserva so e confirmada apos conferencia manual da entrada suficiente pelo profissional.
          </p>
        </div>
      </section>
    </main>
  );
}
