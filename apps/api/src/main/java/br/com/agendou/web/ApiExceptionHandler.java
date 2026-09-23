package br.com.agendou.web;

import br.com.agendou.identity.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ApiErrorResponse> limited(RateLimitExceededException ex, HttpServletRequest request) {
        return ResponseEntity.status(429).header("Retry-After", Long.toString(ex.retryAfter()))
                .body(error(request, "RATE_LIMITED", ex.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiErrorResponse> status(ResponseStatusException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatusCode()).body(error(request, "REQUEST_REJECTED",
                ex.getReason() == null ? "Requisicao nao permitida." : ex.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(HttpServletRequest request) {
        return ResponseEntity.unprocessableEntity().body(error(request, "VALIDATION_ERROR", "Verifique os campos informados."));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiErrorResponse> malformed(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error(request, "MALFORMED_REQUEST", "Requisicao malformada."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> conflict(HttpServletRequest request) {
        return ResponseEntity.status(409).body(error(request, "CONFLICT", "Dados ja registrados ou operacao conflitante."));
    }

    @ExceptionHandler(br.com.agendou.tenancy.TenantMismatchException.class)
    ResponseEntity<ApiErrorResponse> tenant(HttpServletRequest request) {
        return ResponseEntity.status(404).body(error(request, "NOT_FOUND", "Recurso nao encontrado."));
    }

    private ApiErrorResponse error(HttpServletRequest request, String code, String message) {
        return new ApiErrorResponse(code, message, CorrelationIdFilter.id(request));
    }
}
