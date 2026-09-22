package br.com.agendou.web;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.dao.DataIntegrityViolationException;
@RestControllerAdvice
public class ApiExceptionHandler {
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<ApiErrorResponse> status(ResponseStatusException ex) {
  return ResponseEntity.status(ex.getStatusCode()).body(new ApiErrorResponse("REQUEST_REJECTED",ex.getReason()==null?"Requisicao nao permitida.":ex.getReason(),UUID.randomUUID().toString()));
 }
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ApiErrorResponse> validation() {return ResponseEntity.unprocessableEntity().body(new ApiErrorResponse("VALIDATION_ERROR","Verifique os campos informados.",UUID.randomUUID().toString()));}
 @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<ApiErrorResponse> conflict() {return ResponseEntity.status(409).body(new ApiErrorResponse("CONFLICT","Dados ja registrados ou operacao conflitante.",UUID.randomUUID().toString()));}
 @ExceptionHandler(br.com.agendou.tenancy.TenantMismatchException.class) ResponseEntity<ApiErrorResponse> tenant() {return ResponseEntity.status(404).body(new ApiErrorResponse("NOT_FOUND","Recurso nao encontrado.",UUID.randomUUID().toString()));}
}
