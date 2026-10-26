import {defineConfig, devices} from '@playwright/test';
if (!process.env.AGENDOU_E2E_DATA || !process.env.AGENDOU_API_URL || !process.env.AGENDOU_E2E_PORT) {
  throw new Error('Use npm run test:e2e: the isolated Java harness supplies synthetic data and temporary services.');
}
const port=process.env.AGENDOU_E2E_PORT;
export default defineConfig({
 testDir:'./e2e', fullyParallel:false, maxFailures:1, workers:1, retries:0, timeout:120000,
 expect:{timeout:20000}, reporter:[['list'],['html',{open:'never'}],['json',{outputFile:'test-results/results.json'}]],
 use:{baseURL:`http://localhost:${port}`,locale:'pt-BR',timezoneId:'America/Sao_Paulo',trace:'retain-on-failure',screenshot:'only-on-failure'},
 projects:[{name:'desktop',use:{...devices['Desktop Chrome']}},{name:'mobile',use:{...devices['Pixel 7']}}],
 webServer:{command:`node node_modules/next/dist/bin/next build && node node_modules/next/dist/bin/next start -p ${port}`,
  url:`http://localhost:${port}/entrar`,timeout:300000,reuseExistingServer:false,env:{AGENDOU_E2E:'1'},stdout:'pipe',stderr:'pipe'}
});
