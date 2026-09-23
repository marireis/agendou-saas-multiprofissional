package br.com.agendou.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String ATTRIBUTE = CorrelationIdFilter.class.getName();

    public static String id(HttpServletRequest request) {
        Object id = request.getAttribute(ATTRIBUTE);
        if (id == null) {
            id = UUID.randomUUID().toString();
            request.setAttribute(ATTRIBUTE, id);
        }
        return id.toString();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = id(request); // Server-generated: never trust caller-provided IDs.
        response.setHeader("X-Correlation-ID", id);
        MDC.put("correlation_id", id);
        try { chain.doFilter(request, response); }
        finally { MDC.remove("correlation_id"); }
    }
}
