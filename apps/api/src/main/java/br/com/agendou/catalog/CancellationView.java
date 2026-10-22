package br.com.agendou.catalog;

import br.com.agendou.tenancy.TenantContext;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Projection shared by professional and customer; internal operator/correlation stay private. */
final class CancellationView {
 static void add(JdbcTemplate jdbc,UUID booking,Map<String,Object> result,long net){
  var rows=jdbc.query("SELECT reason,retained_cents,created_at FROM booking_cancellations WHERE tenant_id=? AND booking_id=?",(r,n)->Map.<String,Object>of("reason",r.getString(1),"retainedCents",r.getLong(2),"at",r.getTimestamp(3).toInstant()),TenantContext.require(),booking);
  if(rows.isEmpty())return;var cancellation=rows.getFirst();long pending=Math.max(0,net-((Number)cancellation.get("retainedCents")).longValue());result.put("cancellation",cancellation);result.put("refundDueCents",pending);result.put("financialStatus",pending>0?"REFUND_DUE":"CANCELED_SETTLED");
 }
}
