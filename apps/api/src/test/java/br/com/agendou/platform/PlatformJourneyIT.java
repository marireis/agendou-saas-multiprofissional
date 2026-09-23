package br.com.agendou.platform;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.agendou.identity.AuthService;
import br.com.agendou.billing.PlanCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(properties={"agendou.mfa-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","agendou.mail-worker-initial-delay=3600000","agendou.trial-worker-delay=3600000"})
@AutoConfigureMockMvc
@Testcontainers
class PlatformJourneyIT {
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17").withUsername("agendou_app").withPassword("agendou_app");
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.flyway.url",postgres::getJdbcUrl);}
    @Autowired MockMvc mvc; @Autowired AuthService auth; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json;
    @Autowired TestClock clock; @Autowired PlatformAccess access; @Autowired PlatformBillingService billing; @Autowired PlatformMfaService mfa;
    private final String password="synthetic-password-123";
    JdbcTemplate owner() {return new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()));}
    @BeforeEach void reset() {clock.now.set(Instant.ofEpochSecond(Math.floorDiv(Instant.now().getEpochSecond(),300)*300+60));owner().update("DELETE FROM auth_rate_limits");}
    record User(UUID id,UUID tenant,String email) {}
    User user(boolean platform) {
        String email=UUID.randomUUID()+"@example.test";auth.register(email,password,"Studio sintético","studio-"+UUID.randomUUID());
        owner().update("UPDATE app_users SET verified=true WHERE email=?",email);
        UUID id=owner().queryForObject("SELECT id FROM app_users WHERE email=?",UUID.class,email);
        UUID tenant=owner().queryForObject("SELECT tenant_id FROM memberships WHERE user_id=?",UUID.class,id);
        if(platform) owner().update("INSERT INTO platform_roles(user_id,role,granted_by,reason) VALUES (?,'SUPER_ADMIN','integration-test','Synthetic fixture only')",id);
        return new User(id,tenant,email);
    }
    Cookie login(User user) throws Exception {return mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("email",user.email,"password",password)))).andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");}
    String enroll(User user,Cookie cookie) throws Exception {
        String body=mvc.perform(post("/api/v1/platform/mfa/enrollment").cookie(cookie).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("password",password))))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("secret").asText();
    }
    Cookie verify(Cookie cookie,String secret) throws Exception {
        return mvc.perform(post("/api/v1/platform/mfa/verify").cookie(cookie).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("code",Totp.code(secret,clock.instant().getEpochSecond()/30)))))
            .andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
    }
    MockHttpSession verifiedSession(User user) {
        String secret=mfa.enroll(user.id,password,"test").get("secret");UUID version=mfa.verify(user.id,Totp.code(secret,clock.instant().getEpochSecond()/30),"test");
        MockHttpSession session=new MockHttpSession();access.grant(user.id,version,session);return session;
    }
    String decision(UUID id,String action,String reference) throws Exception {
        Map<String,Object> body=new HashMap<>();body.put("id",id);body.put("action",action);body.put("reason","Conferência manual realizada em teste");
        if(reference!=null) {body.put("reference",reference);body.put("amountCents",9900);body.put("planCode","BASIC");}
        return json.writeValueAsString(body);
    }

    @Test void ordinaryAccountsCannotEnrollListOrDecideAndRuntimeCannotGrantPrivileges() throws Exception {
        User ordinary=user(false);Cookie cookie=login(ordinary);
        mvc.perform(get("/api/v1/platform/tenants")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/platform/session").cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/platform/tenants").cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/platform/mfa/enrollment").cookie(cookie).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("password",password)))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/platform/tenants/"+ordinary.tenant+"/decisions").cookie(cookie).with(csrf()).contentType("application/json").content(decision(UUID.randomUUID(),"SUSPEND",null))).andExpect(status().isForbidden());
        assertThatThrownBy(()->jdbc.update("INSERT INTO platform_roles(user_id,role,granted_by,reason) VALUES (?,'SUPER_ADMIN','bad','Unauthorized action')",ordinary.id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForList("SELECT * FROM platform_tenants(?,0)",ordinary.id)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM subscriptions",Long.class)).isZero();
    }

    @Test void enrollmentReplayExpiryReloginAndRoleRevocationAreEnforced() throws Exception {
        User admin=user(true);Cookie cookie=login(admin);
        mvc.perform(get("/api/v1/platform/tenants").cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/platform/mfa/enrollment").cookie(cookie).contentType("application/json").content(json.writeValueAsString(Map.of("password",password)))).andExpect(status().isForbidden());
        String secret=enroll(admin,cookie);Cookie elevated=verify(cookie,secret);
        assertThat(elevated.getValue()).isNotEqualTo(cookie.getValue());
        mvc.perform(get("/api/v1/platform/tenants").cookie(elevated)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/platform/mfa/verify").cookie(elevated).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("code",Totp.code(secret,clock.instant().getEpochSecond()/30))))).andExpect(status().isBadRequest());
        clock.now.set(clock.instant().plusSeconds(901));
        mvc.perform(get("/api/v1/platform/tenants").cookie(elevated)).andExpect(status().isForbidden());
        elevated=verify(elevated,secret);
        // A successful password login must discard prior MFA, even when reusing an existing session.
        var reLogin=mvc.perform(post("/api/v1/auth/login").cookie(elevated).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("email",admin.email,"password",password)))).andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
        mvc.perform(get("/api/v1/platform/tenants").cookie(reLogin)).andExpect(status().isForbidden());
        clock.now.set(clock.instant().plusSeconds(30));elevated=verify(reLogin,secret);
        owner().update("DELETE FROM platform_roles WHERE user_id=?",admin.id);
        mvc.perform(get("/api/v1/platform/tenants").cookie(elevated)).andExpect(status().isForbidden());
    }

    @Test void mfaFailuresRemainRateLimitedAndPendingEnrollmentExpires() throws Exception {
        User admin=user(true);Cookie cookie=login(admin);String secret=enroll(admin,cookie);
        String invalid=String.format("%06d",(Integer.parseInt(Totp.code(secret,clock.instant().getEpochSecond()/30))+123)%1000000);
        for(int n=0;n<4;n++) mvc.perform(post("/api/v1/platform/mfa/verify").cookie(cookie).with(csrf()).contentType("application/json").content("{\"code\":\""+invalid+"\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/platform/mfa/verify").cookie(cookie).with(csrf()).contentType("application/json").content("{\"code\":\""+invalid+"\"}")).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        clock.now.set(clock.instant().plusSeconds(601));
        mvc.perform(post("/api/v1/platform/mfa/verify").cookie(cookie).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("code",Totp.code(secret,clock.instant().getEpochSecond()/30))))).andExpect(status().isBadRequest());
    }

    @Test void paymentIsAuditedIdempotentAndPreservesProfileAndTrial() throws Exception {
        User admin=user(true),professional=user(false);Cookie cookie=login(admin);cookie=verify(cookie,enroll(admin,cookie));
        var original=owner().queryForMap("SELECT trial_started_at,trial_ends_at FROM subscriptions WHERE tenant_id=?",professional.tenant);
        String reference="PAY-"+UUID.randomUUID();UUID id=UUID.randomUUID();String body=decision(id,"CONFIRM_PAYMENT",reference);
        String url="/api/v1/platform/tenants/"+professional.tenant+"/decisions";
        mvc.perform(post(url).cookie(cookie).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.planCode").value("BASIC"));
        var first=owner().queryForMap("SELECT * FROM subscriptions WHERE tenant_id=?",professional.tenant);
        mvc.perform(post(url).cookie(cookie).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk());
        assertThat(owner().queryForMap("SELECT * FROM subscriptions WHERE tenant_id=?",professional.tenant)).isEqualTo(first);
        assertThat(owner().queryForMap("SELECT trial_started_at,trial_ends_at FROM subscriptions WHERE tenant_id=?",professional.tenant)).isEqualTo(original);
        assertThat(owner().queryForObject("SELECT count(*) FROM public_profiles WHERE tenant_id=?",Long.class,professional.tenant)).isEqualTo(1);
        mvc.perform(post(url).cookie(cookie).with(csrf()).contentType("application/json").content(body.replace("9900","10000"))).andExpect(status().isConflict());
        mvc.perform(post(url).cookie(cookie).with(csrf()).contentType("application/json").content(decision(UUID.randomUUID(),"CONFIRM_PAYMENT",reference))).andExpect(status().isConflict());
        assertThat(owner().queryForMap("SELECT * FROM subscriptions WHERE tenant_id=?",professional.tenant)).isEqualTo(first);
        assertThat(owner().queryForObject("SELECT count(*) FROM billing_decisions WHERE tenant_id=?",Long.class,professional.tenant)).isEqualTo(1);
        assertThatThrownBy(()->jdbc.update("DELETE FROM billing_decisions")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE platform_audit SET action='tampered'")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        mvc.perform(get("/api/v1/platform/tenants/"+professional.tenant).cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.decisions[0].action").value("CONFIRM_PAYMENT"));
        mvc.perform(get("/api/v1/platform/audit").cookie(cookie)).andExpect(status().isOk());
        Cookie ordinary=login(professional);
        mvc.perform(get("/api/v1/subscriptions/"+admin.tenant).cookie(ordinary)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/subscriptions/"+professional.tenant+"/confirm-payment").cookie(ordinary).with(csrf())).andExpect(status().isForbidden());
    }

    @Test void suspensionBlocksProfessionalAndDoesNotCreateNewEntitlement() throws Exception {
        User admin=user(true),professional=user(false);var session=verifiedSession(admin);Cookie ordinary=login(professional);
        var suspension=new PlatformBillingService.Decision(UUID.randomUUID(),PlatformBillingService.Action.SUSPEND,"Suspensão operacional de teste",null,null,null);
        billing.decide(admin.id,session,professional.tenant,suspension,"test");
        mvc.perform(patch("/api/v1/admin/profile").cookie(ordinary).with(csrf()).contentType("application/json").content("{\"name\":\"Blocked\",\"description\":\"\",\"timezone\":\"UTC\"}")).andExpect(status().isForbidden());
        clock.now.set(clock.instant().plusSeconds(8*86400));access.grant(admin.id,owner().queryForObject("SELECT version FROM platform_mfa WHERE user_id=?",UUID.class,admin.id),session);
        var restored=billing.decide(admin.id,session,professional.tenant,new PlatformBillingService.Decision(UUID.randomUUID(),PlatformBillingService.Action.REACTIVATE,"Suspensão resolvida em teste",null,null,null),"test");
        assertThat(restored.get("status")).isEqualTo("TRIAL_EXPIRED_BLOCKED");
    }

    @Test void concurrentDecisionsExtendPaidPeriodOnlyOnce() throws Exception {
        User admin=user(true),professional=user(false);var session=verifiedSession(admin);
        var decision=new PlatformBillingService.Decision(UUID.randomUUID(),PlatformBillingService.Action.CONFIRM_PAYMENT,"Pagamento conferido em teste","CONCURRENT-"+UUID.randomUUID(),9900L,PlanCode.BASIC);
        try(var executor=Executors.newFixedThreadPool(4)) {
            var calls=new ArrayList<Callable<Map<String,Object>>>();for(int n=0;n<8;n++) calls.add(()->billing.decide(admin.id,session,professional.tenant,decision,"test"));
            for(var future:executor.invokeAll(calls)) assertThat(future.get().get("status")).isEqualTo("PAID_ACTIVE");
        }
        assertThat(owner().queryForObject("SELECT paid_until FROM subscriptions WHERE tenant_id=?",Timestamp.class,professional.tenant).toInstant()).isEqualTo(clock.instant().plusSeconds(30*86400));
        assertThat(owner().queryForObject("SELECT count(*) FROM billing_decisions WHERE tenant_id=?",Long.class,professional.tenant)).isEqualTo(1);
        clock.now.set(clock.instant().plusSeconds(30*86400));
        mvc.perform(get("/api/v1/admin/subscription").cookie(login(professional))).andExpect(jsonPath("$.status").value("PAST_DUE"));
    }

    @Test void concurrentTotpConsumesCodeOnce() throws Exception {
        User admin=user(true);String secret=mfa.enroll(admin.id,password,"test").get("secret"),code=Totp.code(secret,clock.instant().getEpochSecond()/30);
        try(var executor=Executors.newFixedThreadPool(4)) {
            var calls=new ArrayList<Callable<Boolean>>();for(int n=0;n<8;n++) calls.add(()->{try{mfa.verify(admin.id,code,"test");return true;}catch(org.springframework.web.server.ResponseStatusException ex){return false;}});
            int successes=0;for(var future:executor.invokeAll(calls))if(future.get())successes++;
            assertThat(successes).isEqualTo(1);
        }
    }

    static class TestClock extends Clock {
        final AtomicReference<Instant> now=new AtomicReference<>(Instant.now());
        public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId zone){return this;} public Instant instant(){return now.get();}
    }
    @TestConfiguration static class Time { @Bean @Primary TestClock testClock(){return new TestClock();} }
}
