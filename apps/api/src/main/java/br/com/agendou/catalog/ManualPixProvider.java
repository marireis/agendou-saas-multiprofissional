package br.com.agendou.catalog;

import br.com.agendou.tenancy.TenantContext;
import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ManualPixProvider implements PaymentProvider {
 private final JdbcTemplate jdbc;
 private final Clock clock;
 public ManualPixProvider(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}
 public Map<String,Object> view(UUID bookingId){
  var rows=jdbc.query("""
   SELECT p.id,p.status,p.amount_due_cents,p.deadline_at,b.price_cents,b.timezone,b.status,
    b.pix_snapshot->>'keyType',b.pix_snapshot->>'pixKey',b.pix_snapshot->>'recipientName',b.pix_snapshot->>'paymentInstructions'
   FROM payment_intents p JOIN bookings b ON b.tenant_id=p.tenant_id AND b.id=p.booking_id
   WHERE p.tenant_id=? AND p.booking_id=?
   """,(r,n)->{
    var deadline=r.getTimestamp(4).toInstant();boolean expired="EXPIRED".equals(r.getString(2))||"EXPIRED".equals(r.getString(7))||!clock.instant().isBefore(deadline);
    Map<String,Object> result=new LinkedHashMap<>();result.put("id",r.getObject(1,UUID.class));result.put("provider","MANUAL_PIX");result.put("status",expired?"EXPIRED":"AWAITING_PAYMENT");
    result.put("totalCents",r.getLong(5));result.put("depositCents",r.getLong(3));result.put("remainingAfterDepositCents",r.getLong(5)-r.getLong(3));
    result.put("deadline",deadline);result.put("timezone",r.getString(6));result.put("paymentAvailable",false);
    // A preview of the accepted data, never a claim that funds were received.
    if(!expired){result.put("keyType",r.getString(8));result.put("pixKey",r.getString(9));result.put("recipientName",r.getString(10));result.put("instructions",r.getString(11));}
    return result;
   },TenantContext.require(),bookingId);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Dados de pagamento não encontrados.");return rows.getFirst();
 }
}
