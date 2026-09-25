package br.com.agendou.catalog;
import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class SlotGeneratorTest {
 final LocalDate date=LocalDate.of(2026,9,28);
 final Instant now=Instant.parse("2026-09-26T00:00:00Z");
 AvailabilityController.Period period(String a,String b){return new AvailabilityController.Period(LocalTime.parse(a),LocalTime.parse(b));}
 AvailabilityController.Schedule schedule(int gap,AvailabilityController.Period... periods){return new AvailabilityController.Schedule(List.of(new AvailabilityController.Day(1,List.of(periods))),List.of(),gap);}
 SlotGenerator.Result generate(AvailabilityController.Schedule s,SlotGenerator.Service service){return SlotGenerator.generate(s,service,ZoneId.of("UTC"),date,now,List.of());}
 @Test void gapChangesSequenceButNotAlternativeChoices(){
  var service=new SlotGenerator.Service(30,0,0);var s=schedule(30,period("09:00","12:00"));
  var result=generate(s,service);assertThat(result.candidates()).hasSize(11);
  assertThat(result.sequence().stream().map(x->x.start().atOffset(ZoneOffset.UTC).toLocalTime())).containsExactly(LocalTime.of(9,0),LocalTime.of(10,0),LocalTime.of(11,0));
  assertThat(generate(schedule(60,period("09:00","12:00")),service).sequence()).hasSize(2);
 }
 @Test void buffersFitInsideEachPeriodAndBreakIsNeverCrossed(){
  var result=generate(schedule(0,period("09:00","10:00"),period("11:00","12:00")),new SlotGenerator.Service(30,15,15));
  assertThat(result.candidates().stream().map(s->s.start().atOffset(ZoneOffset.UTC).toLocalTime())).containsExactly(LocalTime.of(9,15),LocalTime.of(11,15));
 }
 @Test void exceptionReplacesWeekAndEmptyExceptionClosesDay(){
  var weekly=schedule(0,period("09:00","12:00"));var service=new SlotGenerator.Service(30,0,0);
  assertThat(generate(new AvailabilityController.Schedule(weekly.weekly(),List.of(new AvailabilityController.ExceptionDay(date,"",List.of())),0),service).candidates()).isEmpty();
  assertThat(generate(new AvailabilityController.Schedule(weekly.weekly(),List.of(new AvailabilityController.ExceptionDay(date,"",List.of(period("15:00","15:30")))),0),service).candidates()).hasSize(1);
 }
 @Test void leadTimeInclusiveAndHorizonExclusive(){
  var s=schedule(0,period("09:00","10:00"));var service=new SlotGenerator.Service(30,0,0);
  var r=SlotGenerator.generate(s,service,ZoneId.of("UTC"),date,Instant.parse("2026-09-27T09:00:00Z"),List.of());assertThat(r.candidates().getFirst().start()).isEqualTo(Instant.parse("2026-09-28T09:00:00Z"));
  assertThat(SlotGenerator.generate(s,service,ZoneId.of("UTC"),date,Instant.parse("2026-07-30T00:00:00Z"),List.of()).candidates()).isEmpty();
 }
 @Test void gapAgainstOccupiedUsesMaximumNotAdditionAndHalfOpenBoundaries(){
  var service=new SlotGenerator.Service(30,10,20);var occupied=new SlotGenerator.Occupied(Instant.parse("2026-09-28T10:00:00Z"),Instant.parse("2026-09-28T10:30:00Z"),10,20);
  var equal=new SlotGenerator.Slot(Instant.parse("2026-09-28T11:00:00Z"),Instant.parse("2026-09-28T11:30:00Z"));
  assertThat(SlotGenerator.conflicts(equal,service,occupied,30)).isFalse();assertThat(SlotGenerator.conflicts(equal,service,occupied,60)).isTrue();
  var before=new SlotGenerator.Slot(Instant.parse("2026-09-28T09:00:00Z"),Instant.parse("2026-09-28T09:30:00Z"));assertThat(SlotGenerator.conflicts(before,service,occupied,30)).isFalse();
 }
 @Test void timezoneConversionAndCivilGrid(){
  var r=SlotGenerator.generate(schedule(0,period("09:07","10:00")),new SlotGenerator.Service(30,0,0),ZoneId.of("America/Sao_Paulo"),date,now,List.of());
  assertThat(r.candidates().getFirst().start()).isEqualTo(Instant.parse("2026-09-28T12:15:00Z"));
 }
 @Test void dstGapAndOverlapAreNotSilentlyAdjusted(){
  for(LocalDate day:List.of(LocalDate.of(2026,3,8),LocalDate.of(2026,11,1))){
   var s=new AvailabilityController.Schedule(List.of(new AvailabilityController.Day(7,List.of(period("00:00","04:00"),period("09:00","10:00")))),List.of(),0);
   var r=SlotGenerator.generate(s,new SlotGenerator.Service(30,0,0),ZoneId.of("America/New_York"),day,day.minusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant(),List.of());
   assertThat(r.candidates()).hasSize(3);assertThat(r.candidates().getFirst().start().atZone(ZoneId.of("America/New_York")).getHour()).isEqualTo(9);
  }
 }
}
