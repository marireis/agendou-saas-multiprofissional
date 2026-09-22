package br.com.agendou.tenancy;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.*;
import org.springframework.web.servlet.HandlerMapping;
class TenantContextInterceptorTest {
 private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
 private final TenantContextInterceptor interceptor=new TenantContextInterceptor(jdbc);
 @AfterEach void clear(){TenantContext.clear();}
 @Test void anonymousCannotSelectTenant(){
  var request=new MockHttpServletRequest("GET","/api/v1/admin/subscription");
  assertThatThrownBy(()->interceptor.preHandle(request,new MockHttpServletResponse(),new Object())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThat(TenantContext.current()).isEmpty();
 }
 @Test @SuppressWarnings("unchecked") void usesMembershipAndClearsContext(){
  UUID user=UUID.randomUUID(),tenant=UUID.randomUUID();
  when(jdbc.query(anyString(),any(RowMapper.class),eq(user))).thenReturn(List.of(tenant));
  var request=new MockHttpServletRequest("GET","/api/v1/admin/subscription");request.setUserPrincipal(()->user.toString());
  interceptor.preHandle(request,new MockHttpServletResponse(),new Object());assertThat(TenantContext.require()).isEqualTo(tenant);
  interceptor.afterCompletion(request,new MockHttpServletResponse(),new Object(),null);assertThat(TenantContext.current()).isEmpty();
 }
 @Test @SuppressWarnings("unchecked") void rejectsAnotherTenantInPath(){
  UUID user=UUID.randomUUID();when(jdbc.query(anyString(),any(RowMapper.class),eq(user))).thenReturn(List.of(UUID.randomUUID()));
  var request=new MockHttpServletRequest("GET","/api/v1/subscriptions/other");request.setUserPrincipal(()->user.toString());
  request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,Map.of("tenantId",UUID.randomUUID().toString()));
  assertThatThrownBy(()->interceptor.preHandle(request,new MockHttpServletResponse(),new Object())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThat(TenantContext.current()).isEmpty();
 }
}
