package com.geer.snowboard.v2.sharedkernel;

public record Actor(String id, String role, String name) {
    public void require(String expected) {
        if (!expected.equals(role)) throw new BusinessProblem(403, "此账号无权执行该操作");
    }
}
