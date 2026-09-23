package br.com.agendou.identity;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@Configuration
@EnableScheduling
public class SecurityConfig {
 @Bean org.springframework.security.core.userdetails.UserDetailsService userDetailsService() {
  return username -> { throw new org.springframework.security.core.userdetails.UsernameNotFoundException("Use o fluxo de autenticacao da aplicacao."); };
 }
 @Bean Clock clock() { return Clock.systemUTC(); }
 @Bean PasswordEncoder passwordEncoder() { return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8(); }
 @Bean HttpSessionSecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }
 @Bean SecurityFilterChain security(HttpSecurity http, HttpSessionSecurityContextRepository contexts,
   AuthRateLimiter limiter, com.fasterxml.jackson.databind.ObjectMapper json) throws Exception {
  return http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/v1/auth/**", "/api/v1/health", "/error").permitAll()
    .requestMatchers("/api/v1/subscriptions/*/trial", "/api/v1/subscriptions/*/confirm-payment").denyAll()
    .anyRequest().authenticated())
   .securityContext(context -> context.securityContextRepository(contexts))
   .requestCache(cache -> cache.disable())
   .addFilterBefore(new AuthRateLimitFilter(limiter,json), org.springframework.security.web.csrf.CsrfFilter.class)
   .exceptionHandling(errors -> errors
    .authenticationEntryPoint((req, res, ex) -> { res.setStatus(401); res.setContentType("application/json"); json.writeValue(res.getOutputStream(),new br.com.agendou.web.ApiErrorResponse("UNAUTHENTICATED","Entre na sua conta.",br.com.agendou.web.CorrelationIdFilter.id(req))); })
    .accessDeniedHandler((req, res, ex) -> { res.setStatus(403); res.setContentType("application/json"); json.writeValue(res.getOutputStream(),new br.com.agendou.web.ApiErrorResponse("FORBIDDEN","Acesso negado ou token CSRF invalido.",br.com.agendou.web.CorrelationIdFilter.id(req))); }))
   .logout(logout -> logout.logoutUrl("/api/v1/auth/logout").logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
   .build();
 }
}
