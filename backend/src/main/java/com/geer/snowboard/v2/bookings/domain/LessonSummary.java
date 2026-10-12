package com.geer.snowboard.v2.bookings.domain;

import com.geer.snowboard.v2.sharedkernel.BusinessProblem;
import java.time.Instant;

public record LessonSummary(String bookingId, State state, Content content, long version,
                            Instant updatedAt, Instant publishedAt) {
    public enum State { NOT_STARTED, DRAFT, PUBLISHED }
    public record Content(String practiceContent, String improvementAreas, String reinforcementExercises) {
        public Content {
            practiceContent = normalize(practiceContent, "本节练习内容");
            improvementAreas = normalize(improvementAreas, "待加强");
            reinforcementExercises = normalize(reinforcementExercises, "课后强化练习");
        }
        private static String normalize(String value, String label) {
            if (value == null) throw new BusinessProblem(400, "请提供" + label);
            String clean = value.strip();
            if (clean.codePointCount(0, clean.length()) > 3000)
                throw new BusinessProblem(400, label + "最多 3000 个字符");
            return clean;
        }
        public void requireComplete() {
            if (practiceContent.isBlank() || improvementAreas.isBlank() || reinforcementExercises.isBlank())
                throw new BusinessProblem(400, "请填写本节练习内容、待加强和课后强化练习后再发布");
        }
    }
    public static boolean completed(String bookingStatus, Instant endAt, Instant now) {
        return "CONFIRMED".equals(bookingStatus) && !endAt.isAfter(now);
    }
    public static LessonSummary empty(String bookingId) {
        return new LessonSummary(bookingId, State.NOT_STARTED, new Content("", "", ""), 0, null, null);
    }
    public LessonSummary write(Content next, long expectedVersion, boolean publish, Instant now) {
        if (publish || state == State.PUBLISHED) next.requireComplete();
        State target = publish || state == State.PUBLISHED ? State.PUBLISHED : State.DRAFT;
        if (state == target && content.equals(next)) return this;
        if (expectedVersion != version) throw new BusinessProblem(409, "总结已在其他窗口更新，请读取最新版本后再保存");
        if (publish && state == State.PUBLISHED) throw new BusinessProblem(409, "总结已发布，请使用保存修改更新内容");
        return new LessonSummary(bookingId, target, next, version + 1, now,
                publishedAt != null ? publishedAt : publish ? now : null);
    }
}
