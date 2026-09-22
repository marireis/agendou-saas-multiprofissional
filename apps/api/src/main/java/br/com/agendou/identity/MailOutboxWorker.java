package br.com.agendou.identity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@Component
public class MailOutboxWorker {
 private final JdbcTemplate jdbc; private final JavaMailSender mail; private final String from;
 public MailOutboxWorker(JdbcTemplate jdbc,JavaMailSender mail,@Value("${agendou.mail-from}") String from) {this.jdbc=jdbc;this.mail=mail;this.from=from;}
 @Scheduled(fixedDelayString="${agendou.mail-worker-delay:5000}") @Transactional public void deliver() {
  var messages=jdbc.query("SELECT id,recipient,subject,body FROM mail_outbox WHERE sent_at IS NULL AND next_attempt_at<=now() AND attempts<10 ORDER BY next_attempt_at LIMIT 10 FOR UPDATE SKIP LOCKED",(rs,n)->new Message(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4)));
  for(var message:messages) {
   var email=new SimpleMailMessage(); email.setFrom(from); email.setTo(message.recipient());email.setSubject(message.subject());email.setText(message.body());
   try {mail.send(email);jdbc.update("UPDATE mail_outbox SET sent_at=now(),body='',recipient='' WHERE id=?",message.id());}
   catch(org.springframework.mail.MailException ex) {jdbc.update("UPDATE mail_outbox SET attempts=attempts+1,next_attempt_at=now()+interval '1 minute' WHERE id=?",message.id());}
  }
 }
 private record Message(UUID id,String recipient,String subject,String body) {}
}
