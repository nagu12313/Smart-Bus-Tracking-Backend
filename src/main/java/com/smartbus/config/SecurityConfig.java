package com.smartbus.config;

import com.smartbus.security.JwtAuthenticationFilter;
import com.smartbus.security.JwtTokenProvider;
import com.smartbus.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security Configuration
 * - Stateless JWT authentication
 * - Role-based access control
 * - CORS for React frontend
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — not needed for stateless JWT APIs
            .csrf(csrf -> csrf.disable())

            // CORS configuration for React frontend
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Stateless session — no server-side sessions
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints — no token required
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/locations/**").permitAll()
                .requestMatchers("/ws/**", "/ws-tracking/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/api-docs/**", "/swagger-ui.html").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // GPS device endpoint — authenticated via device token (custom header), not JWT
                .requestMatchers(HttpMethod.POST, "/api/tracking/location", "/api/tracking/device/location").permitAll()

                // Admin & Support endpoints
                .requestMatchers("/api/admin/support/**").hasAnyRole("ADMIN", "SCHOOL_ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST,   "/api/buses/**").hasAnyRole("ADMIN","SCHOOL_ADMIN")
                .requestMatchers(HttpMethod.PUT,    "/api/buses/**").hasAnyRole("ADMIN","SCHOOL_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/buses/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST,   "/api/drivers/**").hasAnyRole("ADMIN","SCHOOL_ADMIN")
                .requestMatchers(HttpMethod.PUT,    "/api/drivers/**").hasAnyRole("ADMIN","SCHOOL_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/drivers/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST,   "/api/students/**").hasAnyRole("ADMIN","SCHOOL_ADMIN")
                .requestMatchers(HttpMethod.PUT,    "/api/students/**").hasAnyRole("ADMIN","SCHOOL_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/students/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST,   "/api/routes/**").hasAnyRole("ADMIN","SCHOOL_ADMIN")
                .requestMatchers(HttpMethod.PUT,    "/api/routes/**").hasAnyRole("ADMIN","SCHOOL_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/routes/**").hasRole("ADMIN")

                // Trip management — driver and admin
                .requestMatchers("/api/trips/**").hasAnyRole("ADMIN","SCHOOL_ADMIN","DRIVER")

                // Transport Chatbot — all authenticated roles (PARENT, STUDENT, DRIVER, ADMIN, SCHOOL_ADMIN)
                .requestMatchers("/api/transport-chat/**").authenticated()

                // All other authenticated requests
                .anyRequest().authenticated()
            )

            // Authentication provider
            .authenticationProvider(authenticationProvider())

            // Add JWT filter before username/password filter
            .addFilterBefore(jwtAuthenticationFilter(),
                             UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Allow React frontend origins (exact URLs and Vercel wildcard patterns)
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        config.setAllowedOriginPatterns(origins);

        config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","PATCH","OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization","X-Device-Token","X-Bus-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
