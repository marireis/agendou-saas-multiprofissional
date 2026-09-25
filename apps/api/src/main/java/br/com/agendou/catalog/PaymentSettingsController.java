package br.com.agendou.catalog;

import br.com.agendou.billing.SubscriptionService;
import br.com.agendou.tenancy.*;
import br.com.agendou.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/payment-settings")
public class PaymentSettingsController {
 private final JdbcTemplate jdbc;private final TenantSessionConfigurer tenants;private final SubscriptionService subscriptions;private final PixKeyValidator keys;
 public PaymentSettingsController(JdbcTemplate jdbc,TenantSessionConfigurer tenants,SubscriptionService subscriptions,PixKeyValidator keys){this.jdbc=jdbc;this.tenants=tenants;this.subscriptions=subscriptions;this.keys=keys;}
 @GetMapping @Transactional(readOnly=true) public ResponseEntity<Object> get(){
  tenants.applyCurrentTenant();var row=current();
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(row.isEmpty()?Map.of("configured",false,"version",0):view(row));
 }
 @GetMapping("/history") @Transactional(readOnly=true) public ResponseEntity<Object> history(@RequestParam(defaultValue="0") int offset){
  if(offset<0||offset>100000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Página inválida.");
  tenants.applyCurrentTenant();
  // No keys, recipient names or free-text policy/reason in audit listing.
  var rows=jdbc.query("SELECT version,actor_id,changed_fields,enabled,created_at,correlation_id FROM payment_settings_versions WHERE tenant_id=? ORDER BY version DESC LIMIT 50 OFFSET ?",
   (r,n)->Map.of("version",r.getInt(1),"actorId",r.getObject(2,UUID.class),"changedFields",Arrays.asList((String[])r.getArray(3).getArray()),"enabled",r.getBoolean(4),"createdAt",r.getTimestamp(5).toInstant().toString(),"correlationId",r.getString(6)),TenantContext.require(),offset);
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(rows);
 }
 @PutMapping @Transactional public ResponseEntity<Object> save(@Valid @RequestBody Edit body,HttpServletRequest request){
  UUID tenant=TenantContext.require();subscriptions.requireOperational(tenant);tenants.applyCurrentTenant();
  String key=keys.normalize(body.keyType(),body.pixKey());
  String recipient=body.recipientName().trim(),instructions=body.paymentInstructions().trim(),policy=body.cancellationPolicy().trim(),reason=body.changeReason().trim();
  if(recipient.length()<2 || instructions.length()<10 || policy.length()<10 || reason.length()<10)throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Complete o recebedor, as instruções, a política e o motivo da alteração.");
  var old=current();int version=old.isEmpty()?0:((Number)old.get("version")).intValue();
  if(body.version()!=version)throw new ResponseStatusException(HttpStatus.CONFLICT,"Configuração alterada em outra janela. Recarregue e revise antes de salvar.");
  Map<String,Object> next=new LinkedHashMap<>();next.put("key_type",body.keyType().name());next.put("pix_key",key);next.put("recipient_name",recipient);next.put("payment_instructions",instructions);next.put("cancellation_policy",policy);next.put("enabled",body.enabled());
  List<String> changes=next.entrySet().stream().filter(e->!Objects.equals(e.getValue(),old.get(e.getKey()))).map(Map.Entry::getKey).toList();
  if(changes.isEmpty())return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(view(old));
  UUID actor=UUID.fromString(request.getUserPrincipal().getName());
  jdbc.update(connection->{var statement=connection.prepareStatement("""
   INSERT INTO payment_settings_versions(tenant_id,version,key_type,pix_key,recipient_name,payment_instructions,cancellation_policy,enabled,actor_id,change_reason,changed_fields,correlation_id)
   VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
   """);statement.setObject(1,tenant);statement.setInt(2,version+1);statement.setString(3,body.keyType().name());statement.setString(4,key);statement.setString(5,recipient);statement.setString(6,instructions);statement.setString(7,policy);statement.setBoolean(8,body.enabled());statement.setObject(9,actor);statement.setString(10,reason);statement.setArray(11,connection.createArrayOf("text",changes.toArray(String[]::new)));statement.setString(12,CorrelationIdFilter.id(request));return statement;});
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(view(current()));
 }
 private Map<String,Object> current(){var rows=jdbc.queryForList("SELECT * FROM payment_settings_versions WHERE tenant_id=? ORDER BY version DESC LIMIT 1",TenantContext.require());return rows.isEmpty()?Map.of():rows.getFirst();}
 private Map<String,Object> view(Map<String,Object> row){return Map.of("configured",true,"version",row.get("version"),"keyType",row.get("key_type"),"pixKey",row.get("pix_key"),"recipientName",row.get("recipient_name"),"paymentInstructions",row.get("payment_instructions"),"cancellationPolicy",row.get("cancellation_policy"),"enabled",row.get("enabled"),"updatedAt",((Timestamp)row.get("created_at")).toInstant().toString());}
 public record Edit(@NotNull @Min(0) Integer version,@NotNull PixKeyValidator.KeyType keyType,@NotBlank @Size(max=100) String pixKey,
  @NotBlank @Size(min=2,max=100) String recipientName,@NotBlank @Size(min=10,max=2000) String paymentInstructions,@NotBlank @Size(min=10,max=4000) String cancellationPolicy,
  @NotNull Boolean enabled,@NotNull @AssertTrue Boolean confirmed,@NotBlank @Size(min=10,max=500) String changeReason){
  @Override public String toString(){return "PaymentSettingsEdit[redacted]";}
 }
}
