package sayt.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    public static final String CORRELATION = "shop.correlation";

    @ExceptionHandler(Problem.class)
    ResponseEntity<?> problem(Problem error, HttpServletRequest request) {
        var response = ResponseEntity.status(error.status).header("Cache-Control", "no-store");
        if (error.details.containsKey("retryAfter")) {
            response.header("Retry-After", error.details.get("retryAfter").toString());
        }
        return response.body(body(error.code, request, error.details));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException error, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError field : error.getBindingResult().getFieldErrors()) {
            fields.put(field.getField(), "INVALID_VALUE");
        }
        return ResponseEntity.badRequest().body(body("VALIDATION", request, Map.of("fieldErrors", fields)));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, ServletRequestBindingException.class,
        ConstraintViolationException.class, MethodArgumentTypeMismatchException.class,
        org.springframework.web.method.annotation.HandlerMethodValidationException.class})
    ResponseEntity<?> invalid(Exception error, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(body("VALIDATION", request, Map.of()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<?> stale(HttpServletRequest request) {
        return problem(Problem.conflict("VERSION_CONFLICT"), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> duplicate(HttpServletRequest request) {
        return problem(Problem.conflict("DATA_CONFLICT"), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<?> missing(HttpServletRequest request) {
        return problem(Problem.missing(), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<?> tooLarge(HttpServletRequest request) {
        return ResponseEntity.status(413).body(body("PAYLOAD_TOO_LARGE", request, Map.of()));
    }

    @ExceptionHandler({CannotCreateTransactionException.class, DataAccessResourceFailureException.class,
        QueryTimeoutException.class})
    ResponseEntity<?> unavailable(HttpServletRequest request) {
        return ResponseEntity.status(503).header("Retry-After", "5")
            .body(body("TEMPORARY_UNAVAILABLE", request, Map.of()));
    }

    private static Map<String, Object> body(String code, HttpServletRequest request, Map<String, ?> details) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", code);
        body.put("correlationId", correlation(request));
        body.putAll(details);
        return body;
    }

    public static UUID correlation(HttpServletRequest request) {
        if (request.getAttribute(CORRELATION) instanceof UUID id) return id;
        var id = UUID.randomUUID();
        request.setAttribute(CORRELATION, id);
        return id;
    }

    public static void send(HttpServletRequest request, HttpServletResponse response, int status, String code)
        throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("""
            {"code":"%s","message":"%s","correlationId":"%s"}
            """.formatted(code, code, correlation(request)));
    }
}
