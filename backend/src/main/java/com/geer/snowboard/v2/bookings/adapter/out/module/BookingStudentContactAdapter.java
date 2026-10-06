package com.geer.snowboard.v2.bookings.adapter.out.module;

import com.geer.snowboard.v2.bookings.application.port.out.BookingStudentContacts;
import com.geer.snowboard.v2.identity.application.port.in.StudentContactOperations;
import com.geer.snowboard.v2.sharedkernel.Actor;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class BookingStudentContactAdapter implements BookingStudentContacts {
    private final StudentContactOperations contacts;
    public BookingStudentContactAdapter(StudentContactOperations contacts) { this.contacts = contacts; }
    @Override public String currentPhone(Actor student) { return contacts.current(student).phone(); }
    @Override public Map<String, String> phonesForCoach(Actor coach, Set<String> authorizedStudentIds) {
        return contacts.phonesForCoach(coach, authorizedStudentIds);
    }
}
