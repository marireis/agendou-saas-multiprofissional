package br.com.agendou.billing;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import br.com.agendou.tenancy.TenantSessionConfigurer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SubscriptionService {
 private final SubscriptionRepository repository;
 private final TrialPolicy policy;
 private final Clock clock;
 private final JdbcTemplate jdbc;
 private final TenantSessionConfigurer tenants;
 public SubscriptionService(SubscriptionRepository repository,Clock clock,JdbcTemplate jdbc,TenantSessionConfigurer tenants) {
  this.repository=repository; this.clock=clock; this.policy=new TrialPolicy(clock); this.jdbc=jdbc; this.tenants=tenants;
 }
 private void lock(UUID tenant) {
  tenants.assertMatchesCurrentTenant(tenant); tenants.applyCurrentTenant();
  jdbc.queryForObject("SELECT id FROM tenants WHERE id=? FOR UPDATE",UUID.class,tenant);
 }
 @Transactional public Subscription startPremiumTrial(UUID tenant) {
  lock(tenant);
  if(repository.findByTenantId(tenant).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Tenant ja possui assinatura.");
  Subscription subscription=repository.save(policy.startTrial(tenant,PlanCode.PREMIUM_TOP));
  event(tenant,"TRIAL_STARTED"); return subscription;
 }
 @Transactional public Subscription current(UUID tenant) {
  lock(tenant);
  Subscription subscription=repository.findByTenantId(tenant).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Assinatura nao encontrada."));
  Subscription refreshed=policy.refreshStatus(subscription);
  if(refreshed.status()!=subscription.status()) {
   repository.save(refreshed); event(tenant,refreshed.status().name());
   if(refreshed.status()==SubscriptionStatus.TRIAL_EXPIRED_BLOCKED) jdbc.update("UPDATE subscriptions SET blocked_at=? WHERE tenant_id=?",java.sql.Timestamp.from(clock.instant()),tenant);
  }
  return refreshed;
 }
 // Uso interno apenas; nao exposto ate existir autorizacao de plataforma com MFA.
 @Transactional public Subscription confirmPayment(UUID tenant) {
  Subscription subscription=current(tenant);
  if(subscription.status()==SubscriptionStatus.SUSPENDED || subscription.status()==SubscriptionStatus.CANCELED)
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Assinatura exige decisao administrativa.");
  var base=subscription.paidUntil()!=null && subscription.paidUntil().isAfter(clock.instant())?subscription.paidUntil():clock.instant();
  Subscription paid=repository.save(subscription.paidUntil(base.plus(Duration.ofDays(30))));
  jdbc.update("UPDATE subscriptions SET blocked_at=NULL,reactivated_at=? WHERE tenant_id=?",java.sql.Timestamp.from(clock.instant()),tenant);
  event(tenant,"PAYMENT_CONFIRMED"); return paid;
 }
 public void requireOperational(UUID tenant) {
  var status=current(tenant).status();
  if(status!=SubscriptionStatus.TRIAL_ACTIVE && status!=SubscriptionStatus.TRIAL_EXPIRING && status!=SubscriptionStatus.PAID_ACTIVE)
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Assinatura bloqueada. Seus dados estao preservados.");
 }
 private void event(UUID tenant,String type) {
  jdbc.update("INSERT INTO subscription_events(id,tenant_id,subscription_id,event_type) SELECT ?,tenant_id,id,? FROM subscriptions WHERE tenant_id=?",UUID.randomUUID(),type,tenant);
 }
}
