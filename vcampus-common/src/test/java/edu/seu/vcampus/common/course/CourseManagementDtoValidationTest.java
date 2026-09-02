package edu.seu.vcampus.common.course;
import org.junit.jupiter.api.Test; import java.math.BigDecimal; import java.time.*; import static org.assertj.core.api.Assertions.assertThatCode; import static org.assertj.core.api.Assertions.assertThatThrownBy;
class CourseManagementDtoValidationTest {
 @Test void termAdjustmentMustStartStrictlyAfterEnrollment(){Instant a=Instant.EPOCH,b=a.plusSeconds(10);assertThatThrownBy(()->new CreateTermCommand("T","Term",LocalDate.now(),LocalDate.now().plusDays(1),a,b,b,b.plusSeconds(1),"ACTIVE")).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->new UpdateTermCommand("id","T","Term",LocalDate.now(),LocalDate.now().plusDays(1),a,b,b,b.plusSeconds(1),"ACTIVE",0)).isInstanceOf(IllegalArgumentException.class);}
 @Test void commandsRejectBlankIdentifiersAndPagingOverflow(){assertThatThrownBy(()->new CreateCourseCommand(" ","Name",BigDecimal.ONE,1,null,true)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->new UpdateCourseCommand(" ","C","N",BigDecimal.ONE,1,null,true,0)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->new CourseCatalogQuery(null,null,Integer.MAX_VALUE,100)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->new AdjustmentAuditQuery(null,null,null,null,Integer.MAX_VALUE,100)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->new OfferingSearchQuery(null,null,null,false,Integer.MAX_VALUE,100)).isInstanceOf(IllegalArgumentException.class);}
 @Test void pagingRequiresTheWholeRequestedPageToFitAnIntegerOffsetRange(){
  assertThatCode(()->new OfferingSearchQuery(null,null,null,false,21_474_835,100)).doesNotThrowAnyException();
  assertThatCode(()->new CourseCatalogQuery(null,null,21_474_835,100)).doesNotThrowAnyException();
  assertThatCode(()->new AdjustmentAuditQuery(null,null,null,null,21_474_835,100)).doesNotThrowAnyException();
  assertThatThrownBy(()->new OfferingSearchQuery(null,null,null,false,21_474_836,100)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new CourseCatalogQuery(null,null,21_474_836,100)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new AdjustmentAuditQuery(null,null,null,null,21_474_836,100)).isInstanceOf(IllegalArgumentException.class);
 }

 @Test void catalogAndTermCommandsEnforceSchemaTextWidths(){
  Instant a=Instant.EPOCH,b=a.plusSeconds(10),c=b.plusSeconds(10),d=c.plusSeconds(10);
  assertThatCode(()->new CreateCourseCommand("C".repeat(24),"N".repeat(128),BigDecimal.ONE,1,null,true)).doesNotThrowAnyException();
  assertThatThrownBy(()->new CreateCourseCommand("C".repeat(25),"N",BigDecimal.ONE,1,null,true)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new CreateCourseCommand("C","N".repeat(129),BigDecimal.ONE,1,null,true)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new UpdateCourseCommand("i".repeat(37),"C","N",BigDecimal.ONE,1,null,true,0)).isInstanceOf(IllegalArgumentException.class);
  assertThatCode(()->new CreateTermCommand("T".repeat(24),"N".repeat(64),LocalDate.EPOCH,LocalDate.EPOCH.plusDays(1),a,b,c,d,"ACTIVE")).doesNotThrowAnyException();
  assertThatThrownBy(()->new CreateTermCommand("T".repeat(25),"N",LocalDate.EPOCH,LocalDate.EPOCH.plusDays(1),a,b,c,d,"ACTIVE")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new CreateTermCommand("T","N".repeat(65),LocalDate.EPOCH,LocalDate.EPOCH.plusDays(1),a,b,c,d,"ACTIVE")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new UpdateTermCommand("i".repeat(37),"T","N",LocalDate.EPOCH,LocalDate.EPOCH.plusDays(1),a,b,c,d,"ACTIVE",0)).isInstanceOf(IllegalArgumentException.class);
 }

 @Test void offeringAndEnrollmentCommandsEnforceIdentifierAndLabelWidths(){
  var validSchedule=java.util.List.of(new CreateOfferingCommand.ScheduleInput("MONDAY",1,2,1,16,"R".repeat(64)));
  assertThatCode(()->new CreateOfferingCommand("t".repeat(36),"c".repeat(36),"u".repeat(36),"N".repeat(64),20,"OPEN",validSchedule)).doesNotThrowAnyException();
  assertThatThrownBy(()->new CreateOfferingCommand("t".repeat(37),"c","u","N",20,"OPEN",validSchedule)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new CreateOfferingCommand("t","c".repeat(37),"u","N",20,"OPEN",validSchedule)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new CreateOfferingCommand("t","c","u".repeat(37),"N",20,"OPEN",validSchedule)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new CreateOfferingCommand("t","c","u","N".repeat(65),20,"OPEN",validSchedule)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new CreateOfferingCommand.ScheduleInput("MONDAY",1,2,1,16,"R".repeat(65))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new UpdateOfferingCommand("o".repeat(37),"t","c","u","N",20,"OPEN",0,java.util.List.of())).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new EnrollCommand("o".repeat(37))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new LateAddCommand("o".repeat(37))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new DropCommand("e".repeat(37),0)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new ChangeOfferingCommand("e","o".repeat(37),0)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new RetakeCommand("o".repeat(37))).isInstanceOf(IllegalArgumentException.class);
 }

 @Test void courseQueriesRejectIdentifiersWiderThanTheSchema(){
  assertThatThrownBy(()->new OfferingSearchQuery("t".repeat(37),null,null,false,0,20)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new AdjustmentAuditQuery("s".repeat(37),null,null,null,0,20)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new AdjustmentAuditQuery(null,"t".repeat(37),null,null,0,20)).isInstanceOf(IllegalArgumentException.class);
 }
}
