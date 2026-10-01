package com.geer.snowboard.v2.catalog.adapter.out.persistence;

import com.geer.snowboard.v2.catalog.application.port.in.CourseOperations.Course;
import com.geer.snowboard.v2.catalog.application.port.out.CourseStore;
import com.geer.snowboard.v2.sharedkernel.BusinessProblem;
import com.geer.snowboard.v2.sharedkernel.Page;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCourseStore implements CourseStore {
    private static final RowMapper<Course> MAPPER = (rs, row) -> new Course(
            rs.getString("id"), rs.getString("coach_id"), rs.getString("title"), rs.getString("description"),
            rs.getBigDecimal("price_amount"), rs.getString("currency"), rs.getBoolean("active"));
    private final JdbcTemplate jdbc;
    public JdbcCourseStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Course findById(String id) {
        return jdbc.query("SELECT * FROM catalog_course WHERE id=?", MAPPER, id).stream().findFirst().orElse(null);
    }
    @Override public Course lockById(String id) {
        return jdbc.query("SELECT * FROM catalog_course WHERE id=? FOR UPDATE", MAPPER, id)
                .stream().findFirst().orElse(null);
    }
    @Override public Course findByKey(String coachId, String key) {
        return jdbc.query("SELECT * FROM catalog_course WHERE coach_id=? AND idempotency_key=?", MAPPER, coachId, key)
                .stream().findFirst().orElse(null);
    }
    @Override public String fingerprint(String coachId, String key) {
        return jdbc.query("SELECT request_fingerprint FROM catalog_course WHERE coach_id=? AND idempotency_key=?",
                rs -> rs.next() ? rs.getString(1) : null, coachId, key);
    }
    @Override public boolean insert(Course course, String key, String fingerprint) {
        return jdbc.update("INSERT IGNORE INTO catalog_course (id,coach_id,title,description,price_amount,currency,idempotency_key,request_fingerprint,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                course.id(), course.coachId(), course.title(), course.description(), course.priceAmount(),
                course.currency(), key, fingerprint, Instant.now()) == 1;
    }
    @Override public Page<Course> list(String coachId, int limit, String cursor) {
        if (cursor != null && !cursor.matches("[0-9a-fA-F-]{36}")) throw new BusinessProblem(400, "无效分页游标");
        String sql = "SELECT * FROM catalog_course WHERE (? IS NULL OR coach_id=?) AND (? IS NOT NULL OR active=TRUE) AND (? IS NULL OR id<?) ORDER BY id DESC LIMIT ?";
        List<Course> rows = jdbc.query(sql, MAPPER, coachId, coachId, coachId, cursor, cursor, limit + 1);
        String next = rows.size() > limit ? rows.get(limit - 1).id() : null;
        return new Page<>(rows.subList(0, Math.min(limit, rows.size())), next);
    }
    @Override public void update(String id, String title, String description, java.math.BigDecimal price) {
        jdbc.update("UPDATE catalog_course SET title=?,description=?,price_amount=? WHERE id=? AND active=TRUE",
                title, description, price, id);
    }
    @Override public void archive(String id) {
        jdbc.update("UPDATE catalog_course SET active=FALSE WHERE id=?", id);
    }
}
