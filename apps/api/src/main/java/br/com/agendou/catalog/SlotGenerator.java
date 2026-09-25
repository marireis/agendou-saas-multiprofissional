package br.com.agendou.catalog;

import java.time.*;
import java.util.*;

/** Pure candidate calculation. No reservation or resource allocation is created here. */
public final class SlotGenerator {
 public record Service(int duration,int before,int after){}
 public record Slot(Instant start,Instant end){}
 public record Occupied(Instant start,Instant end,int before,int after,boolean block){public Occupied(Instant start,Instant end,int before,int after){this(start,end,before,after,false);}}
 public record Result(List<Slot> candidates,List<Slot> sequence){}
 public static Result generate(AvailabilityController.Schedule schedule,Service service,ZoneId zone,LocalDate date,Instant now,List<Occupied> occupied){
  LocalDate today=now.atZone(zone).toLocalDate();
  if(date.isBefore(today)||!date.isBefore(today.plusDays(60)))return new Result(List.of(),List.of());
  var exception=schedule.exceptions().stream().filter(e->e.date().equals(date)).findFirst();
  var periods=exception.isPresent()?exception.get().periods():schedule.weekly().stream().filter(d->d.day()==date.getDayOfWeek().getValue()).findFirst().map(AvailabilityController.Day::periods).orElse(List.of());
  TreeMap<Instant,Slot> slots=new TreeMap<>();
  for(var period:periods){
   LocalDateTime from=date.atTime(period.start()),to=date.atTime(period.end());
   // A civil period crossing an offset transition is conservatively unavailable.
   // Never silently move nonexistent times or choose one of two ambiguous offsets.
   var fromOffsets=zone.getRules().getValidOffsets(from);var toOffsets=zone.getRules().getValidOffsets(to);
   if(fromOffsets.size()!=1||toOffsets.size()!=1)continue;
   Instant lower=from.toInstant(fromOffsets.getFirst()),upper=to.toInstant(toOffsets.getFirst());
   var transition=zone.getRules().nextTransition(lower.minusNanos(1));
   if(transition!=null&&transition.getInstant().isBefore(upper))continue;
   int minute=((period.start().toSecondOfDay()/60+14)/15)*15;
   for(;minute<period.end().toSecondOfDay()/60;minute+=15){
    LocalDateTime local=date.atStartOfDay().plusMinutes(minute);var offsets=zone.getRules().getValidOffsets(local);if(offsets.size()!=1)continue;
    Instant start=local.toInstant(offsets.getFirst()),end=start.plusSeconds(service.duration()*60L);
    if(start.isBefore(now.plusSeconds(86400))||start.minusSeconds(service.before()*60L).isBefore(lower)||end.plusSeconds(service.after()*60L).isAfter(upper))continue;
    Slot slot=new Slot(start,end);
    if(occupied.stream().noneMatch(o->conflicts(slot,service,o,schedule.intervalMinutes())))slots.put(start,slot);
   }
  }
  var candidates=List.copyOf(slots.values());List<Slot> sequence=new ArrayList<>();
  for(Slot slot:candidates){if(sequence.isEmpty()||!conflicts(slot,service,new Occupied(sequence.getLast().start(),sequence.getLast().end(),service.before(),service.after()),schedule.intervalMinutes()))sequence.add(slot);}
  return new Result(candidates,List.copyOf(sequence));
 }
 static boolean conflicts(Slot slot,Service service,Occupied other,int gap){
  if(other.block())gap=0;
  if(!slot.end().isAfter(other.start()))return slot.end().plusSeconds(Math.max(gap,service.after()+other.before())*60L).isAfter(other.start());
  if(!slot.start().isBefore(other.end()))return other.end().plusSeconds(Math.max(gap,other.after()+service.before())*60L).isAfter(slot.start());
  return true;
 }
 private SlotGenerator(){}
}
