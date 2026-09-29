package br.com.agendou.catalog;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
public class BookingExpirationWorker {
 private final BookingExpirationJobs jobs;
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(BookingExpirationWorker.class);
 public BookingExpirationWorker(BookingExpirationJobs jobs){this.jobs=jobs;}
 @Scheduled(fixedDelayString="${agendou.booking-expiration-delay:60000}",initialDelayString="${agendou.booking-expiration-delay:60000}") public void expire(){
  for(int batch=0;batch<20;batch++){
   var next=jobs.claim();if(next.isEmpty())return;var claim=next.get();
   try{int count=jobs.process(claim);if(count>0)log.info("booking_expiration_completed tenant_id={} count={}",claim.tenant(),count);}
   catch(RuntimeException ex){
    log.warn("booking_expiration_failed tenant_id={}",claim.tenant());
    try{jobs.failed(claim);}catch(RuntimeException retryFailure){log.warn("booking_expiration_retry_deferred tenant_id={}",claim.tenant());}
   }
  }
 }
}
