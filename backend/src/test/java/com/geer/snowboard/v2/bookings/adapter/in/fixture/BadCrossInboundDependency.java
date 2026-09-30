package com.geer.snowboard.v2.bookings.adapter.in.fixture;

import com.geer.snowboard.v2.scheduling.adapter.out.fixture.ForeignOutboundAdapter;

/** Deliberately violates both the cross-module API and inbound adapter boundaries. */
public class BadCrossInboundDependency {
    ForeignOutboundAdapter target;
}
