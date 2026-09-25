package br.com.agendou.catalog;

import br.com.agendou.billing.SubscriptionService;
import br.com.agendou.tenancy.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/services")
public class ServiceController {
 private final JdbcTemplate jdbc; private final TenantSessionConfigurer tenants; private final SubscriptionService subscriptions;
 public ServiceController(JdbcTemplate jdbc,TenantSessionConfigurer tenants,SubscriptionService subscriptions){this.jdbc=jdbc;this.tenants=tenants;this.subscriptions=subscriptions;}
 private static final RowMapper<ServiceView> ROW=(r,n)->new ServiceView(r.getObject("id",UUID.class),r.getString("name"),r.getString("description"),r.getInt("duration_minutes"),r.getLong("price_cents"),r.getInt("buffer_before_minutes"),r.getInt("buffer_after_minutes"),r.getInt("deposit_percent"),r.getBoolean("active"),r.getInt("version"),(r.getLong("price_cents")*r.getInt("deposit_percent")+99)/100);
 @GetMapping @Transactional(readOnly=true) public Object list(@RequestParam(defaultValue="0") int offset) {
  if(offset<0 || offset>100000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Página inválida.");
  tenants.applyCurrentTenant();UUID tenant=TenantContext.require();
  return Map.of("items",jdbc.query("SELECT * FROM services WHERE tenant_id=? ORDER BY created_at,id LIMIT 50 OFFSET ?",ROW,tenant,offset),
   "total",jdbc.queryForObject("SELECT count(*) FROM services WHERE tenant_id=?",Long.class,tenant),
   "activeCount",jdbc.queryForObject("SELECT count(*) FROM services WHERE tenant_id=? AND active",Long.class,tenant));
 }
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional public ServiceView create(@Valid @RequestBody Edit body) {
  subscriptions.requireOperational(TenantContext.require());tenants.applyCurrentTenant();UUID id=UUID.randomUUID();
  jdbc.update("""
   INSERT INTO services(id,tenant_id,name,description,duration_minutes,price_cents,buffer_before_minutes,buffer_after_minutes,deposit_percent,active)
   VALUES (?,?,?,?,?,?,?,?,?,?)
   """,id,TenantContext.require(),body.name().trim(),body.description().trim(),body.durationMinutes(),body.priceCents(),body.bufferBeforeMinutes(),body.bufferAfterMinutes(),body.depositPercent(),body.active());
  return find(id);
 }
 @PutMapping("/{id}") @Transactional public ServiceView update(@PathVariable UUID id,@Valid @RequestBody Edit body) {
  subscriptions.requireOperational(TenantContext.require());tenants.applyCurrentTenant();
  find(id);
  if(body.version()==null) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Informe a versão do serviço.");
  int changed=jdbc.update("""
   UPDATE services SET name=?,description=?,duration_minutes=?,price_cents=?,buffer_before_minutes=?,buffer_after_minutes=?,deposit_percent=?,active=?,version=version+1,updated_at=now()
   WHERE tenant_id=? AND id=? AND version=?
   """,body.name().trim(),body.description().trim(),body.durationMinutes(),body.priceCents(),body.bufferBeforeMinutes(),body.bufferAfterMinutes(),body.depositPercent(),body.active(),TenantContext.require(),id,body.version());
  if(changed==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Este serviço foi alterado em outra janela. Recarregue a lista e abra a edição novamente.");
  return find(id);
 }
 private ServiceView find(UUID id) {
  var rows=jdbc.query("SELECT * FROM services WHERE tenant_id=? AND id=?",ROW,TenantContext.require(),id);
  if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Serviço não encontrado.");return rows.getFirst();
 }
 public record Edit(@NotBlank @Size(max=100) String name,@NotNull @Size(max=2000) String description,
  @NotNull @Min(5) @Max(480) Integer durationMinutes,@NotNull @Min(1) @Max(100000000) Long priceCents,
  @NotNull @Min(0) @Max(240) Integer bufferBeforeMinutes,@NotNull @Min(0) @Max(240) Integer bufferAfterMinutes,
  @NotNull @Min(50) @Max(100) Integer depositPercent,@NotNull Boolean active,@Min(0) Integer version) {}
 public record ServiceView(UUID id,String name,String description,int durationMinutes,long priceCents,int bufferBeforeMinutes,int bufferAfterMinutes,int depositPercent,boolean active,int version,long depositCents) {}
}
