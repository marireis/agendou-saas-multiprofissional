package br.com.agendou.catalog;
import java.util.Map;
import java.util.UUID;

/** Called only after ownership validation, inside the tenant transaction. */
public interface PaymentProvider {
 Map<String,Object> view(UUID bookingId);
}
