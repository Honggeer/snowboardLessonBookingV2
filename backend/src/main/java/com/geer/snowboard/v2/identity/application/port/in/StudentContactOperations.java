package com.geer.snowboard.v2.identity.application.port.in;

import com.geer.snowboard.v2.sharedkernel.Actor;
import java.util.Map;
import java.util.Set;

public interface StudentContactOperations {
    record Contact(String phone) {}
    record SaveContact(String phone) {}
    Contact current(Actor actor);
    Contact save(Actor actor, SaveContact command);
    /** Internal module API: callers must supply only students from already authorized bookings. */
    Map<String, String> phonesForCoach(Actor actor, Set<String> studentIds);
}
