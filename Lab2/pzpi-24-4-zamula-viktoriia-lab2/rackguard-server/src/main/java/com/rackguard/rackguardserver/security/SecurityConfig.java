package com.rackguard.rackguardserver.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackguard.rackguardserver.exception.GlobalExceptionHandler.ApiError;
import com.rackguard.rackguardserver.model.User;
import com.rackguard.rackguardserver.repository.CatalogRepository;
import com.rackguard.rackguardserver.repository.UserRepository;
import com.rackguard.rackguardserver.service.CatalogService;
import com.rackguard.rackguardserver.util.Util;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOriginPatterns(List.of("*"));
        c.setAllowedMethods(List.of("*"));
        c.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", c);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwt, UserRepository users,
                                           CatalogRepository doors, CatalogService catalog, ObjectMapper mapper, MessageSource messages,
                                           @Value("${app.device.skew-seconds}") int skewSeconds) throws Exception {

        http.csrf(c -> c.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/login", "/api/health", "/v3/api-docs/**",
                                "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/iot/**").hasRole("DEVICE")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/requests/**", "/api/visits/**").hasRole("VISITOR")
                        .anyRequest().authenticated())
                .addFilterBefore(new DeviceFilter(doors, catalog, skewSeconds), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtFilter(jwt, users), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> error(req, res, 401, "error.unauthorized", mapper, messages))
                        .accessDeniedHandler((req, res, ex) -> error(req, res, 403, "error.forbidden", mapper, messages)));
        return http.build();
    }

    private static void error(HttpServletRequest req, HttpServletResponse res, int status, String code,
                              ObjectMapper mapper, MessageSource messages) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getOutputStream(),
                new ApiError(code, messages.getMessage(code, null, req.getLocale()), OffsetDateTime.now()));
    }

    private static class JwtFilter extends OncePerRequestFilter {
        private final JwtService jwt;
        private final UserRepository users;

        JwtFilter(JwtService jwt, UserRepository users) { this.jwt = jwt; this.users = users; }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest r) { return r.getRequestURI().startsWith("/api/iot/"); }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            String h = req.getHeader("Authorization");
            if (h != null && h.startsWith("Bearer ")) {
                jwt.parse(h.substring(7))
                        .filter(u -> users.findById(u.userId()).map(User::isActive).orElse(false))
                        .ifPresent(u -> SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(u, null,
                                        List.of(new SimpleGrantedAuthority("ROLE_" + u.role().toUpperCase())))));
            }
            chain.doFilter(req, res);
        }
    }

    private static class DeviceFilter extends OncePerRequestFilter {
        private final CatalogRepository doors;
        private final CatalogService catalog;
        private final int skew;

        DeviceFilter(CatalogRepository doors, CatalogService catalog, int skew) {
            this.doors = doors; this.catalog = catalog; this.skew = skew;
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest r) { return !r.getRequestURI().startsWith("/api/iot/"); }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            CachedBody wrapped = new CachedBody(req);
            String id = req.getHeader("X-Device-Id"), ts = req.getHeader("X-Timestamp"), sig = req.getHeader("X-Signature");
            if (id != null && ts != null && sig != null && fresh(ts)) {
                doors.findDoorByDevice(id).ifPresent(door -> {
                    String data = id + "\n" + ts + "\n" + req.getMethod() + "\n" + req.getRequestURI()
                            + "\n" + new String(wrapped.body, StandardCharsets.UTF_8);
                    if (Util.safeEquals(Util.hmacHex(catalog.deviceSecret(id), data), sig.toLowerCase())) {
                        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                                new Auth.DevicePrincipal(id, door.doorId()), null,
                                List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))));
                    }
                });
            }
            chain.doFilter(wrapped, res);
        }

        private boolean fresh(String ts) {
            try {
                return Math.abs(Instant.now().getEpochSecond() - Long.parseLong(ts)) <= skew;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }

    private static class CachedBody extends HttpServletRequestWrapper {
        final byte[] body;

        CachedBody(HttpServletRequest r) throws IOException { super(r); body = r.getInputStream().readAllBytes(); }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public boolean isFinished() { return in.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener l) { }
                @Override public int read() { return in.read(); }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}