package br.com.agendou.billing;
import br.com.agendou.tenancy.TenantContext;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
@RestController
public class SubscriptionController {
 private final SubscriptionService service;
 public SubscriptionController(SubscriptionService service) { this.service=service; }
 @GetMapping("/api/v1/subscriptions/{tenantId}") public View current(@PathVariable UUID tenantId) { return view(service.current(tenantId)); }
 @GetMapping("/api/v1/admin/subscription") public View mine() { return view(service.current(TenantContext.require())); }
 private View view(Subscription s) { return new View(s.tenantId(),s.planCode(),s.status(),s.trialStartedAt(),s.trialEndsAt(),s.paidUntil()); }
 public record View(UUID tenantId,PlanCode planCode,SubscriptionStatus status,Instant trialStartedAt,Instant trialEndsAt,Instant paidUntil) {}
}
