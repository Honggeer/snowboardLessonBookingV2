package com.geer.snowboard.v2.bookings.adapter.out.module;

import com.geer.snowboard.v2.bookings.application.port.out.BookingMailContacts;
import com.geer.snowboard.v2.identity.application.port.in.AccountContactOperations;
import org.springframework.stereotype.Component;

@Component
public class BookingMailContactAdapter implements BookingMailContacts {
    private final AccountContactOperations contacts;

    public BookingMailContactAdapter(AccountContactOperations contacts) {
        this.contacts = contacts;
    }

    @Override
    public String emailForAccount(String accountId) {
        return contacts.emailForAccount(accountId);
    }
}
