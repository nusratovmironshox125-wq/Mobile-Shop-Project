package sayt.common;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class Problem extends RuntimeException {
    public final HttpStatus status;
    public final String code;
    public final Map<String, ?> details;

    public Problem(HttpStatus status, String code, Map<String, ?> details) {
        super(code);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public static Problem bad(String code) {
        return new Problem(HttpStatus.BAD_REQUEST, code, Map.of());
    }

    public static Problem missing() {
        return new Problem(HttpStatus.NOT_FOUND, "NOT_FOUND", Map.of());
    }

    public static Problem conflict(String code) {
        return new Problem(HttpStatus.CONFLICT, code, Map.of());
    }

    public static Problem limited(int seconds) {
        return new Problem(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", Map.of("retryAfter", seconds));
    }

    public static void version(Long expected, long actual) {
        if (expected == null || expected != actual) throw conflict("VERSION_CONFLICT");
    }
}
