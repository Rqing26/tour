package cn.shanhai.travel.config;

import cn.shanhai.travel.model.AccountPrincipal;
import cn.shanhai.travel.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  UserDetailsService userDetailsService(UserRepository users) {
    return name -> {
      var a =
          users
              .byUsername(name)
              .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
      return AccountPrincipal.from(a);
    };
  }

  @Bean
  AuthenticationManager authenticationManager(UserDetailsService details, PasswordEncoder encoder) {
    var provider = new DaoAuthenticationProvider(details);
    provider.setPasswordEncoder(encoder);
    return new ProviderManager(provider);
  }

  @Bean
  SecurityContextRepository contextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  CsrfTokenRepository csrfRepository() {
    return new HttpSessionCsrfTokenRepository();
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      UserRepository users,
      ObjectMapper json,
      SecurityContextRepository contexts,
      CsrfTokenRepository csrf)
      throws Exception {
    http.securityContext(c -> c.securityContextRepository(contexts))
        .csrf(
            c ->
                c.csrfTokenRepository(csrf)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .authorizeHttpRequests(
            a ->
                a.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC).permitAll()
                    .requestMatchers(
                        "/api/auth/csrf",
                        "/api/auth/config",
                        "/api/auth/login",
                        "/api/auth/register")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint((req, res, ex) -> error(json, res, 401, "请先登录"))
                    .accessDeniedHandler(
                        (req, res, ex) ->
                            error(
                                json,
                                res,
                                403,
                                ex instanceof CsrfException ? "会话校验已过期，请刷新页面后重试" : "没有权限执行此操作")))
        .logout(
            l ->
                l.logoutUrl("/api/auth/logout")
                    .invalidateHttpSession(true)
                    .clearAuthentication(true)
                    .deleteCookies("JSESSIONID")
                    .logoutSuccessHandler(
                        (req, res, auth) -> {
                          res.setContentType("application/json;charset=UTF-8");
                          json.writeValue(res.getWriter(), Map.of("message", "已退出登录"));
                        }))
        .formLogin(f -> f.disable())
        .httpBasic(b -> b.disable());
    http.addFilterBefore(
        new OncePerRequestFilter() {
          @Override
          protected void doFilterInternal(
              jakarta.servlet.http.HttpServletRequest request,
              HttpServletResponse response,
              jakarta.servlet.FilterChain chain)
              throws jakarta.servlet.ServletException, IOException {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken)) {
              var account = users.byUsername(auth.getName());
              if (!(auth.getPrincipal() instanceof AccountPrincipal principal)
                  || account.isEmpty()
                  || !account.get().enabled()
                  || account.get().id() != principal.id()
                  || !account.get().passwordHash().equals(principal.password())) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                error(json, response, 401, "登录状态已失效，请重新登录");
                return;
              }
            }
            chain.doFilter(request, response);
          }
        },
        AnonymousAuthenticationFilter.class);
    return http.build();
  }

  private static void error(
      ObjectMapper json, HttpServletResponse response, int status, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json;charset=UTF-8");
    json.writeValue(response.getWriter(), Map.of("message", message));
  }
}
