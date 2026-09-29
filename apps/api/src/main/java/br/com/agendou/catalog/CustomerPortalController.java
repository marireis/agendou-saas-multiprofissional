package br.com.agendou.catalog;

import br.com.agendou.identity.AuthRateLimiter;
import br.com.agendou.tenancy.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/public/{slug}/client")
public class CustomerPortalController {
 private final JdbcTemplate jdbc;
 private final TenantSessionConfigurer tenants;
 private final AuthRateLimiter limiter;
 private final BookingService bookings;
 private final Clock clock;
 private final String publicUrl;
 public CustomerPortalController(JdbcTemplate jdbc,TenantSessionConfigurer tenants,AuthRateLimiter limiter,BookingService bookings,Clock clock,@Value("${agendou.public-url}") String publicUrl){this.jdbc=jdbc;this.tenants=tenants;this.limiter=limiter;this.bookings=bookings;this.clock=clock;this.publicUrl=publicUrl;}
 public record Email(@NotBlank @jakarta.validation.constraints.Email @Size(max=254) String email){}
 public record Token(@NotBlank @Size(max=128) String token,@NotBlank @jakarta.validation.constraints.Email @Size(max=254) String email){}
 public record Access(String slug,String email,Instant expiresAt) implements java.io.Serializable{}
 private UUID bind(String slug,String email){
  UUID tenant=jdbc.queryForObject("SELECT public.resolve_customer_tenant(?,?)",UUID.class,slug,email);
  if(tenant!=null){TenantContext.set(tenant);tenants.applyCurrentTenant();jdbc.queryForObject("SELECT id FROM tenants WHERE id=? FOR UPDATE",UUID.class,tenant);}
  return tenant;
 }
 @PostMapping("/access-links") @Transactional
 public ResponseEntity<?> request(@PathVariable String slug,@Valid @RequestBody Email body){
  String email=body.email().trim().toLowerCase(Locale.ROOT);limiter.checkAccount(email,false);
  try{
   UUID tenant=bind(slug,email);
   if(tenant!=null && Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM customers WHERE tenant_id=? AND email=?)",Boolean.class,tenant,email))){
    var old=jdbc.query("SELECT token_hash FROM customer_portal_tokens WHERE tenant_id=? AND email=?",(r,n)->r.getString(1),tenant,email);
    for(var hash:old)jdbc.update("UPDATE mail_outbox SET status='CANCELED',recipient='',body='' WHERE token_hash=? AND status='PENDING'",hash);
    jdbc.update("DELETE FROM customer_portal_tokens WHERE tenant_id=? AND email=?",tenant,email);
    byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),hash=hash(token);
    var now=Timestamp.from(clock.instant());var expiry=Timestamp.from(clock.instant().plusSeconds(900));
    jdbc.update("INSERT INTO customer_portal_tokens(token_hash,tenant_id,email,expires_at) VALUES (?,?,?,?)",hash,tenant,email,expiry);
    // Email stays inside the fragment, never in access logs or a referrer query string.
    String fragment="token="+token+"&email="+java.net.URLEncoder.encode(email,StandardCharsets.UTF_8);
    jdbc.update("INSERT INTO mail_outbox(id,recipient,subject,body,purpose,token_hash,expires_at,created_at,next_attempt_at) VALUES (?,?,?,?,'CUSTOMER_PORTAL',?,?,?,?)",UUID.randomUUID(),email,"Agendou: meus agendamentos",publicUrl+"/a/"+slug+"/meus-agendamentos#"+fragment+"\nAbra o link e confirme o acesso. Válido por 15 minutos, de uso único.",hash,expiry,now,now);
   }
   return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(Map.of("message","Se houver agendamentos para esse email, enviaremos um link de acesso. Confira também a pasta de spam."));
  }finally{TenantContext.clear();}
 }
 @PostMapping("/access-links/consume") @Transactional
 public ResponseEntity<?> consume(@PathVariable String slug,@Valid @RequestBody Token body,HttpServletRequest request){
  String email=body.email().trim().toLowerCase(Locale.ROOT);
  try{
   UUID tenant=bind(slug,email);
   if(tenant==null)throw invalid();
   var rows=jdbc.query("DELETE FROM customer_portal_tokens WHERE tenant_id=? AND email=? AND token_hash=? AND expires_at>? RETURNING email",(r,n)->r.getString(1),tenant,email,hash(body.token()),Timestamp.from(clock.instant()));
   if(rows.isEmpty())throw invalid();
   jdbc.update("UPDATE mail_outbox SET status='CANCELED',recipient='',body='' WHERE token_hash=? AND status='PENDING'",hash(body.token()));
   request.getSession(true);request.changeSessionId();
   request.getSession().setAttribute("customerPortal",new Access(slug,rows.getFirst(),clock.instant().plusSeconds(3600)));
   return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("message","Acesso verificado."));
  }finally{TenantContext.clear();}
 }
 private Access verified(String slug,HttpServletRequest request){
  var session=request.getSession(false);var value=session==null?null:session.getAttribute("customerPortal");
  if(!(value instanceof Access access)||!access.slug().equals(slug)||!clock.instant().isBefore(access.expiresAt()))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Verifique seu email para acessar seus agendamentos.");
  return access;
 }
 @GetMapping("/bookings") public ResponseEntity<?> list(@PathVariable String slug,@RequestParam(defaultValue="0") int offset,HttpServletRequest request){
  var access=verified(slug,request);
  if(offset<0||offset>10000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Página inválida.");
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(bookings.listForCustomer(slug,access.email(),offset));
 }
 @GetMapping("/bookings/{id}") public ResponseEntity<?> detail(@PathVariable String slug,@PathVariable UUID id,HttpServletRequest request){
  var access=verified(slug,request);
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(bookings.detailForCustomer(slug,access.email(),id));
 }
 @PostMapping("/logout") public ResponseEntity<?> logout(@PathVariable String slug,HttpServletRequest request){
  var session=request.getSession(false);
  if(session!=null){session.removeAttribute("customerPortal");session.removeAttribute("customerReview");session.removeAttribute("customerBookingReceipt");}
  return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
 }
 private static ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Link inválido ou expirado. Solicite outro email.");}
 private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
}
