package com.geer.snowboard.v2.sharedkernel;

import java.util.List;

public record Page<T>(List<T> items, String nextCursor) {}
