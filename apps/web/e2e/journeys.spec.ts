import {test,expect,type Page} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import {readFileSync} from 'node:fs';
const fixtures=JSON.parse(readFileSync(process.env.AGENDOU_E2E_DATA!,'utf8'));
const data=(name:string)=>fixtures[name] as {email:string,password:string,slug:string,date:string};
async function login(page:Page,name:string){const f=data(name);await page.goto('/entrar');await page.getByLabel('Email',{exact:true}).fill(f.email);await page.getByLabel('Senha',{exact:true}).fill(f.password);await page.getByRole('button',{name:'Entrar',exact:true}).click();await expect(page.getByRole('heading',{name:'Seu negócio, no seu ritmo'})).toBeVisible();}
async function accessible(page:Page){const result=await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa']).analyze();expect(result.violations.map(v=>({id:v.id,impact:v.impact,nodes:v.nodes.map(n=>n.target)}))).toEqual([]);}
function localNow(){const d=new Date();return new Intl.DateTimeFormat('sv-SE',{timeZone:'America/Sao_Paulo',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false}).format(d).replace(' ','T');}
test('login por teclado, senha inválida, temas persistentes e acessibilidade',async({page},info)=>{
 await page.goto('/entrar');await accessible(page);
 await page.getByRole('button',{name:'Ativar modo claro'}).click();await expect(page.locator('html')).toHaveAttribute('data-theme','light');await page.reload();await expect(page.locator('html')).toHaveAttribute('data-theme','light');await accessible(page);
 await page.getByLabel('Email',{exact:true}).fill(data(info.project.name).email);await page.getByLabel('Senha',{exact:true}).fill('senha-incorreta');await page.getByLabel('Senha',{exact:true}).press('Enter');await expect(page.getByRole('main').getByRole('alert')).toBeVisible();
 await page.getByLabel('Senha',{exact:true}).fill(data(info.project.name).password);await expect(page.getByRole('button',{name:'Entrar',exact:true})).toBeEnabled();await page.getByLabel('Senha',{exact:true}).press('Tab');await expect(page.getByRole('button',{name:'Entrar',exact:true})).toBeFocused();await page.keyboard.press('Enter');await expect(page.getByRole('heading',{name:'Seu negócio, no seu ritmo'})).toBeVisible();await accessible(page);
 await page.getByRole('button',{name:'Sair',exact:true}).click();await expect(page).toHaveURL(/\/entrar/);await page.goto('/painel/financeiro');await expect(page).toHaveURL(/\/entrar/);
});
test('reserva pública → código → comprovante → conferência → cancelamento e devolução',async({page,browser},info)=>{
 const f=data(info.project.name);const errors:string[]=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto(`/a/${f.slug}/agendar`);await page.getByRole('button',{name:/Consulta.*30 minutos/}).click();
 const date=f.date.split('-').reverse().join('/');
 // The seeded day may fall in next month.
 await page.getByRole('button',{name:'Próximo mês'}).waitFor();
 const day=page.getByRole('button',{name:`${date}, disponível`,exact:true});
 if(!(await day.count())) await page.getByRole('button',{name:'Próximo mês'}).click();
 await day.click();await page.getByRole('button',{name:'09:00',exact:true}).click();
 await page.getByLabel('Nome',{exact:true}).fill('Maria');await page.getByLabel('Sobrenome',{exact:true}).fill('Silva');await page.getByLabel('Telefone com DDD',{exact:true}).fill('11999990000');await page.getByRole('button',{name:'Revisar agendamento'}).click();
 await expect(page.getByRole('heading',{name:'Revise seu atendimento'})).toBeVisible();await page.getByRole('checkbox').check();await page.getByRole('button',{name:'Solicitar reserva temporária'}).click();
 await expect(page.getByRole('heading',{name:'Solicitação registrada'})).toBeVisible();const code=await page.getByLabel('Código privado de acesso').inputValue();expect(code).toHaveLength(43);await accessible(page);
 // Fresh context exercises consultation without relying on the booking cookie.
 const customer=await browser.newContext({...info.project.use,baseURL:info.project.use.baseURL,locale:'pt-BR',timezoneId:'America/Sao_Paulo'});const portal=await customer.newPage();
 try{
  await portal.goto(`/a/${f.slug}/meus-agendamentos`);await portal.getByLabel('Nome',{exact:true}).fill('Maria');await portal.getByLabel('Sobrenome',{exact:true}).fill('Silva');await portal.getByLabel('Telefone com DDD',{exact:true}).fill('11999990000');await portal.getByLabel('Código de acesso',{exact:true}).fill('A'.repeat(43));await portal.getByRole('button',{name:'Ver meu agendamento',exact:true}).click();await expect(portal.getByRole('alert').filter({hasText:'Dados ou código não conferem'})).toBeVisible();
  await portal.getByLabel('Código de acesso',{exact:true}).fill(code);await portal.getByRole('button',{name:'Ver meu agendamento',exact:true}).click();await portal.getByRole('button',{name:/Ver detalhes de Consulta/}).click();
  await portal.getByRole('button',{name:'Ver dados PIX da reserva'}).click();await portal.getByLabel('Comprovante',{exact:true}).setInputFiles(process.env.AGENDOU_E2E_PROOF!);await portal.getByRole('button',{name:'Enviar comprovante'}).click();await expect(portal.getByRole('status').filter({hasText:'Comprovante recebido'})).toBeVisible();await expect(portal.getByRole('link',{name:'Baixar comprovante 1'})).toBeVisible();
  await login(page,info.project.name);await page.getByRole('navigation',{name:'Áreas do painel'}).getByRole('link',{name:'Conferência',exact:true}).click();await page.getByRole('button',{name:'Gerenciar reserva'}).click();await expect(page.getByRole('link',{name:'Baixar comprovante 1'})).toBeVisible();
  const downloading=page.waitForEvent('download');await page.getByRole('link',{name:'Baixar comprovante 1'}).click();const download=await downloading;expect(await download.failure()).toBeNull();
  await page.getByLabel('Valor recebido (R$)',{exact:true}).fill('50,00');await page.getByLabel('Conta recebedora',{exact:true}).fill('Conta sintética');await page.getByLabel('Referência bancária da transação',{exact:true}).fill(`e2e-${info.project.name}`);await page.getByLabel('Data e hora do recebimento',{exact:true}).fill(localNow());await page.getByRole('checkbox',{name:/Conferi este crédito/}).check();await page.getByRole('button',{name:'Registrar recebimento',exact:true}).click();await expect(page.getByRole('status').filter({hasText:'Reserva confirmada'})).toBeVisible();
  await portal.reload();await portal.getByRole('button',{name:/Ver detalhes de Consulta/}).click();await expect(portal.getByText('Seu atendimento está confirmado.',{exact:true})).toBeVisible();
  await page.getByText('Cancelar atendimento',{exact:true}).click();await page.getByLabel('Motivo do cancelamento (visível ao cliente)',{exact:true}).fill('Cancelamento sintético solicitado pelo cliente');await page.getByLabel('Valor a devolver (R$)',{exact:true}).fill('50');await page.getByRole('checkbox',{name:/Revisei a política/}).check();await page.getByRole('button',{name:'Confirmar cancelamento e liberar horário'}).click();await expect(page.getByRole('status').filter({hasText:'Reserva cancelada'})).toBeVisible();
  await page.getByRole('combobox',{name:'Recebimento de origem',exact:true}).selectOption({index:1});await page.getByLabel('Valor devolvido (R$)',{exact:true}).fill('50');await page.getByLabel('Referência bancária da devolução',{exact:true}).fill(`refund-${info.project.name}`);await page.getByLabel('Motivo',{exact:true}).fill('Devolução sintética do cancelamento');await page.getByLabel('Data e hora no fuso deste dispositivo',{exact:true}).fill(localNow());await page.getByRole('checkbox',{name:/Confirmo que já realizei/}).check();await page.getByRole('button',{name:'Registrar devolução',exact:true}).click();await expect(page.getByRole('status').filter({hasText:'Devolução registrada'})).toBeVisible();
  await portal.reload();await portal.getByRole('button',{name:/Ver detalhes de Consulta/}).click();await expect(portal.getByText('Reserva cancelada',{exact:true})).toBeVisible();await portal.getByRole('button',{name:'Sair deste acesso'}).click();await expect(portal.getByRole('heading',{name:'Consulte seu agendamento'})).toBeVisible();
  expect(errors).toEqual([]);
 }finally{await customer.close();}
});
test('navegação administrativa e cadastro/edição de cliente',async({page},info)=>{
 await login(page,info.project.name);
 const nav=page.getByRole('navigation',{name:'Áreas do painel'});
 for(const name of ['Notificações','Minha página','Serviços','PIX e política','Clientes','Financeiro','Conferência','Calendário','Horários','Publicação','Compartilhar','Assinatura']){
  const link=nav.getByRole('link',{name,exact:true});const href=await link.getAttribute('href');await link.click();await expect(page).toHaveURL(new RegExp(`${href}$`));await expect(page.locator('main h1')).toBeVisible();await expect(page.getByRole('main').getByRole('alert').filter({hasText:/\S/})).toHaveCount(0);
 }
 await nav.getByRole('link',{name:'Clientes',exact:true}).click();await page.getByRole('button',{name:'Novo cliente',exact:true}).click();await page.getByLabel('Nome e sobrenome',{exact:true}).fill('Cliente Navegador');await page.getByLabel('Telefone com DDD (obrigatório sem email)',{exact:true}).fill('11988887777');await page.getByRole('button',{name:'Salvar cliente'}).click();await expect(page.getByRole('heading',{name:'Cliente Navegador',exact:true})).toBeVisible();
 await page.getByRole('button',{name:'Editar cadastro'}).click();await page.getByLabel('Nome e sobrenome',{exact:true}).fill('Cliente Navegador Atualizado');await page.getByRole('button',{name:'Salvar cliente'}).click();await expect(page.getByRole('heading',{name:'Cliente Navegador Atualizado'})).toBeVisible();await accessible(page);
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth+1)).toBe(true);
});
test('recuperação de senha responde sem revelar conta e superadmin exige autorização',async({page},info)=>{
 await page.goto('/recuperar');await page.getByLabel('Email',{exact:true}).fill(`unknown-${info.project.name}@example.test`);await page.getByRole('button',{name:'Continuar'}).click();await expect(page.getByRole('status')).toHaveText('Se houver uma conta, enviaremos as instruções por email.');
 await login(page,info.project.name);await page.goto('/plataforma');await expect(page.getByRole('button',{name:/Confirmar pagamento/})).toHaveCount(0);await expect(page.getByText('Acesso exclusivo da administração da plataforma.',{exact:true})).toBeVisible();
});
test('carga local limitada de leitura, sem erros HTTP',async({request},info)=>{
 const times:number[]=[];const statuses:number[]=[];const start=performance.now();
 await Promise.all(Array.from({length:4},async()=>{for(let i=0;i<5;i++){const at=performance.now();const response=await request.get(`/api/v1/public/pages/${data(info.project.name).slug}`);times.push(performance.now()-at);statuses.push(response.status());}}));
 times.sort((a,b)=>a-b);
 await info.attach('carga-sintetica.json',{body:JSON.stringify({requests:20,concurrency:4,durationMs:performance.now()-start,p95Ms:times[Math.ceil(times.length*.95)-1],statuses},null,2),contentType:'application/json'});
 expect(statuses).toEqual(Array(20).fill(200));
});
