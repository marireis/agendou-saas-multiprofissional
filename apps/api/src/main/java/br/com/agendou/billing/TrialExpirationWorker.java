package br.com.agendou.billing;
import br.com.agendou.tenancy.TenantContext;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
public class TrialExpirationWorker {
 private final JdbcTemplate jdbc; private final SubscriptionService subscriptions;
 public TrialExpirationWorker(JdbcTemplate jdbc,SubscriptionService subscriptions) {this.jdbc=jdbc;this.subscriptions=subscriptions;}
 @Scheduled(fixedDelayString="${agendou.trial-worker-delay:60000}") public void expire() {
  var tenants=jdbc.query("SELECT DISTINCT tenant_id FROM memberships",(rs,n)->rs.getObject(1,UUID.class));
  for(UUID tenant:tenants) {
   TenantContext.set(tenant);
   try { subscriptions.current(tenant); } finally { TenantContext.clear(); }
  }
 }
}
