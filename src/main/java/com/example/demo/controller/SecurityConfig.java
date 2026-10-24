package com.example.demo.controller;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/register", "/images/**", "/css/**", "/js/**", "/error").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form.loginPage("/").loginProcessingUrl("/login")
                .usernameParameter("userName").defaultSuccessUrl("/home", true).failureUrl("/?error"))
            .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/?logout"))
            .addFilterBefore(new LoginRateLimitFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    // 同一接続元の試行回数を一定時間内に制限する
    static class LoginRateLimitFilter extends OncePerRequestFilter {
        private record Window(long startedAt, int attempts) {}
        private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if ("POST".equals(request.getMethod()) && "/login".equals(path)) {
                long now = System.currentTimeMillis();
                windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() > 300_000);
                Window window = windows.compute(request.getRemoteAddr(), (key, old) ->
                        old == null ? new Window(now, 1) : new Window(old.startedAt(), old.attempts() + 1));
                if (window.attempts() > 10) {
                    response.sendRedirect(request.getContextPath() + "/?limited");
                    return;
                }
            }
            chain.doFilter(request, response);
        }
    }
}
