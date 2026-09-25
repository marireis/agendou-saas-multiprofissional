package br.com.agendou.catalog;
import br.com.agendou.billing.SubscriptionService;
import br.com.agendou.tenancy.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.time.ZoneId;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestController
@RequestMapping("/api/v1/admin/profile")
public class ProfileController {
 private final JdbcTemplate jdbc; private final TenantSessionConfigurer tenants; private final SubscriptionService subscriptions; private final LogoSanitizer logos;private final CalendarService calendar;
 public ProfileController(JdbcTemplate jdbc,TenantSessionConfigurer tenants,SubscriptionService subscriptions,LogoSanitizer logos,CalendarService calendar) {this.calendar=calendar;this.jdbc=jdbc;this.tenants=tenants;this.subscriptions=subscriptions;this.logos=logos;}
 @GetMapping @Transactional(readOnly=true) public Profile get() {
  tenants.applyCurrentTenant();
  return jdbc.queryForObject("""
   SELECT t.display_name,t.slug,p.description,p.timezone,p.contact_email,p.contact_phone,p.service_mode,p.location,p.logo_version
   FROM tenants t JOIN public_profiles p ON p.tenant_id=t.id WHERE t.id=?
   """,(rs,n)->profile(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getObject(9,UUID.class)),TenantContext.require());
 }
 @PatchMapping @Transactional public Profile update(@Valid @RequestBody Edit body) {
  subscriptions.requireOperational(TenantContext.require());
  if(!ZoneId.getAvailableZoneIds().contains(body.timezone())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Escolha um fuso IANA válido, como America/Sao_Paulo.");
  tenants.applyCurrentTenant();
  if(!body.timezone().equals(jdbc.queryForObject("SELECT timezone FROM public_profiles WHERE tenant_id=?",String.class,TenantContext.require())))calendar.requireNoFutureHolds();
  jdbc.update("UPDATE tenants SET display_name=?,updated_at=now() WHERE id=?",body.name().trim(),TenantContext.require());
  // Null new fields preserve old-client compatibility; empty strings clear a saved draft.
  jdbc.update("""
   UPDATE public_profiles SET description=?,timezone=?,contact_email=coalesce(?,contact_email),
   contact_phone=coalesce(?,contact_phone),service_mode=coalesce(?,service_mode),location=coalesce(?,location),updated_at=now()
   WHERE tenant_id=?
   """,body.description().trim(),body.timezone(),trim(body.contactEmail()),trim(body.contactPhone()),body.serviceMode(),trim(body.location()),TenantContext.require());
  return get();
 }
 @PutMapping("/logo") @Transactional public Profile upload(HttpServletRequest request) throws IOException {
  subscriptions.requireOperational(TenantContext.require());tenants.applyCurrentTenant();
  if(request.getContentLengthLong()>LogoSanitizer.MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"A logomarca deve ter no máximo 2 MB.");
  byte[] clean=logos.sanitize(request.getInputStream());
  jdbc.update("UPDATE public_profiles SET logo_png=?,logo_version=?,updated_at=now() WHERE tenant_id=?",clean,UUID.randomUUID(),TenantContext.require());
  return get();
 }
 @DeleteMapping("/logo") @Transactional public Profile removeLogo() {
  subscriptions.requireOperational(TenantContext.require());tenants.applyCurrentTenant();
  jdbc.update("UPDATE public_profiles SET logo_png=NULL,logo_version=NULL,updated_at=now() WHERE tenant_id=?",TenantContext.require());
  return get();
 }
 @GetMapping("/logo") @Transactional(readOnly=true) public ResponseEntity<byte[]> logo() {
  tenants.applyCurrentTenant();
  byte[] png=jdbc.queryForObject("SELECT logo_png FROM public_profiles WHERE tenant_id=?",byte[].class,TenantContext.require());
  if(png==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Nenhuma logomarca cadastrada.");
  return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
   .header("X-Content-Type-Options","nosniff").header("Content-Disposition","inline; filename=logo.png").body(png);
 }
 private Profile profile(String name,String slug,String description,String timezone,String email,String phone,String mode,String location,UUID logo) {
  List<String> missing=new ArrayList<>();
  if(description.isBlank()) missing.add("Descrição do negócio");
  if(email.isBlank() && phone.isBlank()) missing.add("Email ou telefone de contato");
  if(mode.equals("UNSET")) missing.add("Modalidade de atendimento");
  if((mode.equals("IN_PERSON") || mode.equals("HYBRID")) && location.isBlank()) missing.add("Local de atendimento");
  // Identity, description, contact and mode/location are required; branding is optional.
  int complete=1+(!description.isBlank()?1:0)+(!email.isBlank() || !phone.isBlank()?1:0)
   +(!mode.equals("UNSET") && (mode.equals("ONLINE") || !location.isBlank())?1:0);
  return new Profile(name,slug,description,timezone,email,phone,mode,location,logo,complete*25,missing.isEmpty(),missing);
 }
 private String trim(String value) {return value==null?null:value.trim();}
 public record Profile(String name,String slug,String description,String timezone,String contactEmail,String contactPhone,String serviceMode,String location,UUID logoVersion,int profileProgress,boolean profileComplete,List<String> missingFields) {}
 public record Edit(@NotBlank @Size(max=100) String name,@NotNull @Size(max=2000) String description,@NotBlank @Size(max=100) String timezone,
  @Email @Size(max=254) String contactEmail,
  @Pattern(regexp="^$|^\\+?[0-9][0-9 ()-]{6,23}$") String contactPhone,
  @Pattern(regexp="UNSET|IN_PERSON|ONLINE|HYBRID") String serviceMode,
  @Size(max=500) String location) {}
}
