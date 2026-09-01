package com.easycode.api.error;

import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * Turns exceptions into responses.
 *
 * <p>The previous version had a single {@code @ExceptionHandler(Exception.class)} at the
 * bottom and nothing above it for Spring's own MVC exceptions. Everything Spring threw
 * before reaching a controller — an unparseable enum, a wrong content type, a path
 * variable that wouldn't convert — fell into that catch-all and came back as an identical
 * opaque 500. Two completely different bugs produced byte-identical responses, so fixing
 * one looked exactly like fixing none.
 *
 * <p>Each MVC failure now gets its real status code and says what it choked on. The
 * catch-all still exists, because it must, but it logs a correlation id that appears in
 * the response too — so a 500 on screen can be found in the Railway logs by searching for
 * one string instead of guessing at timestamps.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * When true, unexpected 500s include the exception class and message in the response.
     * Set APP_DEBUG_ERRORS=true on Railway while you are building; turn it off before you
     * put the URL in front of a paying client.
     */
    @Value("${app.debug-errors:false}")
    private boolean debugErrors;

    // ------------------------------------------------------- deliberate failures

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(body(e.getCode(), e.getMessage()));
    }

    // ------------------------------------------------ bad input, not bad server

    /**
     * An unreadable body. This is the one that used to masquerade as a 500: send a JSON
     * enum value the Java enum doesn't declare and Jackson throws here, long before any
     * controller code runs. The cause message names the offending field and value.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException e) {
        String detail = rootMessage(e);
        log.warn("Unreadable request body: {}", detail);
        return ResponseEntity.badRequest()
                .body(body("bad_request", "That request body couldn't be read: " + detail));
    }

    /** A path variable or query param that wouldn't convert — e.g. a bad enum in the URL. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String want = e.getRequiredType() == null ? "another type" : e.getRequiredType().getSimpleName();
        String msg = "'" + e.getValue() + "' isn't a valid " + want + " for " + e.getName();
        log.warn("Type mismatch: {}", msg);
        return ResponseEntity.badRequest().body(body("bad_request", msg));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, Object> out = body("validation_failed", "Some fields need attention");
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult()
                .getFieldErrors()
                .forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        out.put("fields", fields);
        return ResponseEntity.badRequest().body(out);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraint(ConstraintViolationException e) {
        return ResponseEntity.badRequest().body(body("validation_failed", e.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest()
                .body(body("bad_request", "Missing required parameter: " + e.getParameterName()));
    }

    /** Wrong verb on a real path — POST-only vs GET-only is a trap in this API. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethod(HttpRequestMethodNotSupportedException e) {
        String msg = e.getMethod() + " isn't allowed here; try "
                + String.join(" or ", e.getSupportedMethods() == null ? new String[] {"another verb"} : e.getSupportedMethods());
        log.warn("Method not allowed: {}", msg);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body("method_not_allowed", msg));
    }

    /** Almost always a missing Content-Type: application/json on the client side. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMediaType(HttpMediaTypeNotSupportedException e) {
        log.warn("Unsupported media type: {}", e.getContentType());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(body("unsupported_media_type",
                        "Send this as application/json (got " + e.getContentType() + ")"));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoHandler(NoHandlerFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(body("not_found", "No endpoint at " + e.getRequestURL()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrity(DataIntegrityViolationException e) {
        String detail = rootMessage(e);
        log.warn("Data integrity violation: {}", detail, e);
        Map<String, Object> out = body("conflict", "That conflicts with something already saved");
        if (debugErrors) {
            out.put("detail", detail);
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(out);
    }

    // ------------------------------------------------------------- genuine bugs

    /**
     * Anything we didn't anticipate. The correlation id is the point: it is logged and
     * returned, so the 500 you see in DevTools can be grepped for in Railway directly.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAny(Exception e) {
        String ref = Long.toHexString(System.nanoTime()).toUpperCase();
        log.error("Unhandled exception ref={} type={}", ref, e.getClass().getName(), e);

        Map<String, Object> out = body("server_error", "Something went wrong on our end");
        out.put("ref", ref);
        if (debugErrors) {
            out.put("detail", e.getClass().getSimpleName() + ": " + rootMessage(e));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(out);
    }

    // ------------------------------------------------------------------ helpers

    /** The innermost message — the outer wrapper is almost never the useful part. */
    private String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String m = root.getMessage();
        if (m == null) {
            return root.getClass().getSimpleName();
        }
        return m.length() > 400 ? m.substring(0, 400) + "…" : m;
    }

    private Map<String, Object> body(String code, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", code);
        m.put("message", message);
        return m;
    }
}