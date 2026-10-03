package com.geer.snowboard.v2.media.application.port.in;
public interface MediaJobs {
    boolean runOne();
    void scheduleCleanup();
}
