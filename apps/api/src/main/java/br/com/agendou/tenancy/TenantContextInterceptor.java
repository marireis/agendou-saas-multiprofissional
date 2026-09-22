package br.com.agendou.tenancy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Tenant sempre derivado do usuario autenticado e de membership persistida. */
@Component
public class TenantContextInterceptor implements HandlerInterceptor {
 private final JdbcTemplate jdbc;
 public TenantContextInterceptor(JdbcTemplate jdbc) { this.jdbc=jdbc; }
 @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
  TenantContext.clear();
  String path=request.getRequestURI();
  if (!path.startsWith("/api/v1/admin/") && !path.startsWith("/api/v1/subscriptions/")) return true;
  if(request.getUserPrincipal()==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
  UUID user=UUID.fromString(request.getUserPrincipal().getName());
  var memberships=jdbc.query("SELECT tenant_id FROM memberships WHERE user_id=? AND role='ADMIN'",(rs,n)->rs.getObject(1,UUID.class),user);
  if(memberships.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  UUID tenant=memberships.getFirst();
  Object attribute=request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
  if(attribute instanceof Map<?,?> variables && variables.get("tenantId")!=null) {
   UUID requested;
   try { requested=UUID.fromString(variables.get("tenantId").toString()); }
   catch(IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"tenantId invalido."); }
   if(!tenant.equals(requested)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Recurso nao encontrado.");
  }
  TenantContext.set(tenant); return true;
 }
 @Override public void afterCompletion(HttpServletRequest request,HttpServletResponse response,Object handler,Exception ex) { TenantContext.clear(); }
}
