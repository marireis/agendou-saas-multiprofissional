package br.com.agendou.catalog;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
public class BookingExpirationWorker {
 private final JdbcTemplate jdbc;private final BookingService bookings;
 public BookingExpirationWorker(JdbcTemplate jdbc,BookingService bookings){this.jdbc=jdbc;this.bookings=bookings;}
 @Scheduled(fixedDelayString="${agendou.booking-expiration-delay:60000}",initialDelayString="${agendou.booking-expiration-delay:60000}") public void expire(){
  for(UUID tenant:jdbc.query("SELECT DISTINCT tenant_id FROM memberships",(r,n)->r.getObject(1,UUID.class))){
   try{bookings.expireTenant(tenant);}catch(org.springframework.dao.DataAccessException ex){org.slf4j.LoggerFactory.getLogger(BookingExpirationWorker.class).warn("booking_expiration_failed tenant_id={}",tenant);}
  }
 }
}
