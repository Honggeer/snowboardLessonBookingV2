package com.geer.snowboard.v2.identity.application.port.in;

public record AuthenticatedAccount(AccountView view, long credentialVersion) {}
