package sayt.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import org.springframework.stereotype.Component;
import sayt.exception.GlobalExceptionHandler;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class RequestBodyLimit extends OncePerRequestFilter {
    private static final int LIMIT = 64 * 1024;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String type = request.getContentType();
        return type == null || !request.getRequestURI().startsWith("/api/")
            || !type.toLowerCase(Locale.ROOT).contains("json");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        if (request.getContentLengthLong() > LIMIT) {
            GlobalExceptionHandler.send(request, response, 413, "PAYLOAD_TOO_LARGE");
            return;
        }
        byte[] body = request.getInputStream().readNBytes(LIMIT + 1);
        if (body.length > LIMIT) {
            GlobalExceptionHandler.send(request, response, 413, "PAYLOAD_TOO_LARGE");
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                var bytes = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    @Override public int read() { return bytes.read(); }
                    @Override public int read(byte[] target, int offset, int length) {
                        return bytes.read(target, offset, length);
                    }
                    @Override public boolean isFinished() { return bytes.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) {
                        throw new UnsupportedOperationException("Blocking REST request");
                    }
                };
            }
        }, response);
    }
}
