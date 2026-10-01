package com.geer.snowboard.v2.sharedkernel;

public final class BusinessProblem extends RuntimeException {
    private final int status;
    public BusinessProblem(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
}
