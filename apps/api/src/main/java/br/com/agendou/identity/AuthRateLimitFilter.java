package br.com.agendou.identity;

import br.com.agendou.web.ApiErrorResponse;
import br.com.agendou.web.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

// Registered only in Spring Security, not as a second servlet filter.
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private final AuthRateLimiter limiter;
    private final ObjectMapper json;

    public AuthRateLimitFilter(AuthRateLimiter limiter, ObjectMapper json) {
        this.limiter = limiter;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/v1/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            // Forwarded headers are intentionally ignored until trusted ingress is configured.
            limiter.checkPeer(request.getRemoteAddr());
        } catch (RateLimitExceededException ex) {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(ex.retryAfter()));
            response.setContentType("application/json");
            json.writeValue(response.getOutputStream(), new ApiErrorResponse(
                    "RATE_LIMITED", ex.getMessage(), CorrelationIdFilter.id(request)));
            return;
        }
        chain.doFilter(request, response);
    }
}
