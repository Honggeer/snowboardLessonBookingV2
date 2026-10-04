package com.geer.snowboard.v2.catalog.application.port.out;

import com.geer.snowboard.v2.catalog.application.port.in.CourseOperations.Cover;

public interface CourseMedia {
    void save(String owner, String courseId, String assetId);
    Cover published(String courseId, String assetId);
}
