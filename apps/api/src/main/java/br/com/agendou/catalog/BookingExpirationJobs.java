package br.com.agendou.catalog;

import br.com.agendou.tenancy.*;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingExpirationJobs {
 public record Claim(UUID tenant,UUID token){}
 private final JdbcTemplate jdbc;
 private final TenantSessionConfigurer tenants;
 private final BookingService bookings;
 public BookingExpirationJobs(JdbcTemplate jdbc,TenantSessionConfigurer tenants,BookingService bookings){this.jdbc=jdbc;this.tenants=tenants;this.bookings=bookings;}
 @Transactional public Optional<Claim> claim(){
  return jdbc.query("SELECT tenant_id,lease_token FROM public.claim_booking_expiration()",(r,n)->new Claim(r.getObject(1,UUID.class),r.getObject(2,UUID.class))).stream().findFirst();
 }
 @Transactional public int process(Claim claim){
  TenantContext.set(claim.tenant());
  try{
   tenants.applyCurrentTenant();
   jdbc.execute("SET LOCAL lock_timeout='5s'");
   jdbc.execute("SET LOCAL statement_timeout='30s'");
   jdbc.queryForObject("SELECT id FROM tenants WHERE id=? FOR UPDATE",UUID.class,claim.tenant());
   if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT public.lock_booking_expiration(?,?)",Boolean.class,claim.tenant(),claim.token())))return 0;
   int count=bookings.expireCurrent(100);
   jdbc.queryForObject("SELECT public.finish_booking_expiration(?,?,false)",Object.class,claim.tenant(),claim.token());
   return count;
  }finally{TenantContext.clear();}
 }
 @Transactional public void failed(Claim claim){
  jdbc.queryForObject("SELECT public.finish_booking_expiration(?,?,true)",Object.class,claim.tenant(),claim.token());
 }
}
