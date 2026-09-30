package com.geer.snowboard.v2.identity.adapter.in.fixture;

import com.geer.snowboard.v2.identity.application.service.IdentityService;

/** Deliberately invalid test fixture: inbound adapters must use the published port. */
public class BadInboundDependency {
    IdentityService service;

    IdentityService forbiddenReturnType() { return service; }
}
