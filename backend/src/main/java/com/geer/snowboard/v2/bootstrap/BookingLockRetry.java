package com.geer.snowboard.v2.bootstrap;

import com.geer.snowboard.v2.sharedkernel.BusinessProblem;
import java.sql.SQLException;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/** Retries only MySQL deadlocks and lock timeouts after the failed transaction has ended. */
@Component
public final class BookingLockRetry {
    public <T> T run(Supplier<T> operation) {
        for (int attempt = 0; ; attempt++) {
            try { return operation.get(); }
            catch (DataAccessException error) {
                if (!isRetryableLockError(error)) throw error;
                if (attempt >= 2) throw new BusinessProblem(503, "数据库正忙，请稍后使用相同请求键重试");
            }
        }
    }

    private static boolean isRetryableLockError(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && (sql.getErrorCode() == 1205 || sql.getErrorCode() == 1213))
                return true;
        }
        return false;
    }
}
