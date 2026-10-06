package com.geer.snowboard.v2.identity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.geer.snowboard.v2.identity.application.port.in.StudentContactOperations.SaveContact;
import com.geer.snowboard.v2.identity.application.port.out.IdentityStore;
import com.geer.snowboard.v2.identity.application.service.StudentContactService;
import com.geer.snowboard.v2.sharedkernel.Actor;
import com.geer.snowboard.v2.sharedkernel.BusinessProblem;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class StudentContactServiceTest {
    @Test void directUseCasesRejectWrongRolesWithoutContactAccess() {
        var store = mock(IdentityStore.class); var service = new StudentContactService(store);
        Actor student = new Actor("s", "STUDENT", "学员"), coach = new Actor("c", "COACH", "教练");
        assertThatThrownBy(() -> service.current(coach)).isInstanceOfSatisfying(BusinessProblem.class, p -> assertThat(p.status()).isEqualTo(403));
        assertThatThrownBy(() -> service.save(coach, new SaveContact("+14165550123"))).isInstanceOf(BusinessProblem.class);
        assertThatThrownBy(() -> service.phonesForCoach(student, Set.of("s"))).isInstanceOf(BusinessProblem.class);
        assertThatThrownBy(() -> service.current(null)).isInstanceOfSatisfying(BusinessProblem.class, p -> assertThat(p.status()).isEqualTo(401));
        verifyNoInteractions(store);
    }
    @Test void forgedRoleAndMissingAccountCannotReadOrUpdatePhone() {
        var store = mock(IdentityStore.class); var service = new StudentContactService(store);
        when(store.findById("s")).thenReturn(new IdentityStore.Account("s", "学员", "BEGINNER", "test@example.test", "test@example.test", "STUDENT", "unused", Instant.now(), 0));
        assertThatThrownBy(() -> service.phonesForCoach(new Actor("s", "COACH", "伪造角色"), Set.of("other")))
                .isInstanceOfSatisfying(BusinessProblem.class, p -> assertThat(p.status()).isEqualTo(403));
        assertThatThrownBy(() -> service.save(new Actor("missing", "STUDENT", "无账号"), new SaveContact("+14165550123")))
                .isInstanceOfSatisfying(BusinessProblem.class, p -> assertThat(p.status()).isEqualTo(401));
        verify(store, never()).studentPhones(anySet()); verify(store, never()).saveStudentPhone(anyString(), anyString());
    }
    @Test void coachContactLookupIsBoundedToOnePage() {
        var store = mock(IdentityStore.class); var service = new StudentContactService(store);
        when(store.findById("c")).thenReturn(new IdentityStore.Account("c", "教练", null, "coach@example.test", "coach@example.test", "COACH", "unused", Instant.now(), 0));
        var ids = IntStream.range(0, 51).mapToObj(i -> "student-" + i).collect(Collectors.toSet());
        assertThatThrownBy(() -> service.phonesForCoach(new Actor("c", "COACH", "教练"), ids))
                .isInstanceOfSatisfying(BusinessProblem.class, p -> assertThat(p.status()).isEqualTo(400));
        verify(store, never()).studentPhones(anySet());
    }
}
