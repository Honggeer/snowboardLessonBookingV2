package com.geer.snowboard.v2.bookings.application.port.out;

import com.geer.snowboard.v2.sharedkernel.Actor;
import java.util.Map;
import java.util.Set;

public interface BookingStudentContacts {
    String currentPhone(Actor student);
    Map<String, String> phonesForCoach(Actor coach, Set<String> authorizedStudentIds);
}
