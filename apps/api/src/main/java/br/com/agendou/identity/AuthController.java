package br.com.agendou.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
 private final AuthService auth;
 private final HttpSessionSecurityContextRepository contexts;
 private final AuthRateLimiter limiter;
 public AuthController(AuthService auth,HttpSessionSecurityContextRepository contexts,AuthRateLimiter limiter) { this.auth=auth; this.contexts=contexts; this.limiter=limiter; }
 @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken(),"headerName",token.getHeaderName()); }
 @PostMapping("/register") @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
 public void register(@Valid @RequestBody Registration body) { limiter.checkAccount(body.email(),false); auth.register(body.email(),body.password(),body.name(),body.slug()); }
 @PostMapping("/verification-email") @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
 public void resendVerification(@Valid @RequestBody Email body) { limiter.checkAccount(body.email(),false); auth.resendVerification(body.email()); }
 @PostMapping("/verify") public void verify(@Valid @RequestBody Token body) { auth.verify(body.token()); }
 @PostMapping("/login") public void login(@Valid @RequestBody Login body,HttpServletRequest request,HttpServletResponse response) {
  limiter.checkAccount(body.email(),true);
  var user=auth.login(body.email(),body.password());
  request.getSession(); request.changeSessionId();
  br.com.agendou.platform.PlatformAccess.clear(request.getSession());
  var context=SecurityContextHolder.createEmptyContext();
  context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user.toString(),null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
  SecurityContextHolder.setContext(context); contexts.saveContext(context,request,response);
  new org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository().saveToken(null,request,response);
 }
 @PostMapping("/password-reset") @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
 public void requestReset(@Valid @RequestBody Email body) { limiter.checkAccount(body.email(),false); auth.requestReset(body.email()); }
 @PostMapping("/password-reset/confirm") public void reset(@Valid @RequestBody Reset body) { auth.reset(body.token(),body.password()); }
 public record Registration(@NotBlank @jakarta.validation.constraints.Email @Size(max=254) String email,@NotBlank @Size(min=12,max=64) String password,
  @NotBlank @Size(max=100) String name,@NotBlank @Pattern(regexp="[a-z0-9]+(?:-[a-z0-9]+)*") @Size(min=3,max=60) String slug) {}
 public record Login(@NotBlank @jakarta.validation.constraints.Email @Size(max=254) String email,@NotBlank @Size(max=64) String password) {}
 public record Token(@NotBlank @Size(max=128) String token) {}
 public record Email(@NotBlank @jakarta.validation.constraints.Email @Size(max=254) String email) {}
 public record Reset(@NotBlank @Size(max=128) String token,@NotBlank @Size(min=12,max=64) String password) {}
}
