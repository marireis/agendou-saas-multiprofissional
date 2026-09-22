package br.com.agendou.catalog;
import br.com.agendou.billing.SubscriptionService;
import br.com.agendou.tenancy.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/admin/profile")
public class ProfileController {
 private final JdbcTemplate jdbc; private final TenantSessionConfigurer tenants; private final SubscriptionService subscriptions;
 public ProfileController(JdbcTemplate jdbc,TenantSessionConfigurer tenants,SubscriptionService subscriptions) {this.jdbc=jdbc;this.tenants=tenants;this.subscriptions=subscriptions;}
 @GetMapping @Transactional(readOnly=true) public Profile get() {
  tenants.applyCurrentTenant();
  return jdbc.queryForObject("SELECT t.display_name,t.slug,p.description,p.timezone FROM tenants t JOIN public_profiles p ON p.tenant_id=t.id WHERE t.id=?",(rs,n)->new Profile(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4)),TenantContext.require());
 }
 @PatchMapping @Transactional public Profile update(@Valid @RequestBody Edit body) {
  subscriptions.requireOperational(TenantContext.require());
  try { java.time.ZoneId.of(body.timezone()); } catch(java.time.DateTimeException ex) {throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,"Fuso horario invalido.");}
  tenants.applyCurrentTenant();
  jdbc.update("UPDATE tenants SET display_name=?,updated_at=now() WHERE id=?",body.name(),TenantContext.require());
  jdbc.update("UPDATE public_profiles SET description=?,timezone=?,updated_at=now() WHERE tenant_id=?",body.description(),body.timezone(),TenantContext.require());
  return get();
 }
 public record Profile(String name,String slug,String description,String timezone) {}
 public record Edit(@NotBlank @Size(max=100) String name,@NotNull @Size(max=2000) String description,@NotBlank @Size(max=100) String timezone) {}
}
