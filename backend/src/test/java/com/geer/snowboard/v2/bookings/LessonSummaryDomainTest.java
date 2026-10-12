package com.geer.snowboard.v2.bookings;
import static org.assertj.core.api.Assertions.*;
import com.geer.snowboard.v2.bookings.domain.LessonSummary;
import com.geer.snowboard.v2.sharedkernel.BusinessProblem;
import java.time.*;
import org.junit.jupiter.api.Test;
class LessonSummaryDomainTest {
    @Test void completionUsesInstantsAcrossDstAndMidnightRatherThanLocalDate() {
        for(String point:new String[]{"2030-03-10T07:00:00Z","2030-11-03T06:00:00Z","2030-01-08T04:59:59Z"}) {
            Instant end=Instant.parse(point);
            assertThat(LessonSummary.completed("CONFIRMED",end,end.minusNanos(1))).isFalse();
            assertThat(LessonSummary.completed("CONFIRMED",end,end)).isTrue();
            assertThat(LessonSummary.completed("CONFIRMED",end,end.plusNanos(1))).isTrue();
            for(String state:new String[]{"PENDING","REJECTED","CANCELLED_BY_STUDENT"})
                assertThat(LessonSummary.completed(state,end,end)).isFalse();
        }
    }
    @Test void publicationKeepsFirstTimestampAndReplaysNormalizedContentWithoutVersionChange() {
        Instant now=Instant.parse("2030-01-07T18:00:00Z");
        var content=new LessonSummary.Content("  换刃\n重心  "," 加强 "," 练习 ");
        var draft=LessonSummary.empty("id").write(content,0,false,now);
        assertThat(draft.write(new LessonSummary.Content("换刃\n重心","加强","练习"),0,false,now)).isSameAs(draft);
        var published=draft.write(content,1,true,now.plusSeconds(1));
        var updated=published.write(new LessonSummary.Content("新练习","加强","练习"),2,false,now.plusSeconds(2));
        assertThat(updated.publishedAt()).isEqualTo(now.plusSeconds(1));
        assertThat(updated.version()).isEqualTo(3);
        assertThatThrownBy(()->updated.write(new LessonSummary.Content("x","y","z"),2,false,now)).isInstanceOf(BusinessProblem.class);
        assertThatThrownBy(()->updated.write(new LessonSummary.Content("x","y","z"),3,true,now)).isInstanceOf(BusinessProblem.class);
        assertThatThrownBy(()->updated.write(new LessonSummary.Content("","y","z"),3,false,now)).isInstanceOf(BusinessProblem.class);
    }
}
