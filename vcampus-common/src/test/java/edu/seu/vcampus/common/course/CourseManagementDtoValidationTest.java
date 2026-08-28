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
}
