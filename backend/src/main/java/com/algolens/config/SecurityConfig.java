package com.algolens.config;

import com.algolens.dto.ApiErrorResponse;
import com.algolens.security.AppUserDetailsService;
import com.algolens.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless JWT security.
 *
 * <p>Notable choices:
 *
 * <ul>
 *   <li>CSRF is off because there is no cookie-based session to forge against -- the token
 *       travels in an {@code Authorization} header the browser will not attach automatically.</li>
 *   <li>{@code POST /api/executions} is {@code permitAll} so the visualizer works before anyone
 *       signs up. Whether an anonymous run is actually allowed is decided in
 *       {@code ExecutionService} from {@code algolens.execution.allow-anonymous}, which keeps
 *       that product decision in one place instead of split across a URL matcher.</li>
 *   <li>Unauthenticated and forbidden responses are JSON in the same {@link ApiErrorResponse}
 *       shape as every other error, so the frontend has one parser.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public SecurityConfig(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        // Needed only so the H2 console renders in the dev profile.
                        .frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/api/meta/**").permitAll()
                        // Must precede the broad GET permitAll below: submission history is
                        // per-user and must not be reachable anonymously.
                        .requestMatchers(HttpMethod.GET, "/api/problems/*/submissions")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/problems", "/api/problems/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/executions").permitAll()
                        // Same shape as executions: JudgeService itself decides whether an
                        // anonymous submission is actually allowed, from the same
                        // algolens.execution.allow-anonymous flag.
                        .requestMatchers(HttpMethod.POST, "/api/problems/*/submit").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs",
                                "/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        // Everything that is not the API is the single-page app: its bundle and
                        // its client-side routes. Expressed as "not /api/**" rather than a list
                        // of paths, because that list would have to be kept in sync with the
                        // frontend router forever, and forgetting an entry would show a JSON 401
                        // where a page belongs.
                        .requestMatchers(new NegatedRequestMatcher(
                                new AntPathRequestMatcher("/api/**"))).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                                        "Sign in to use this endpoint",
                                        request.getRequestURI()))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, HttpStatus.FORBIDDEN, "FORBIDDEN",
                                        "You do not have access to this resource",
                                        request.getRequestURI())))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeError(jakarta.servlet.http.HttpServletResponse response, HttpStatus status,
            String code, String message, String path) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiErrorResponse.of(status.value(), code, message, path));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Cost 10 is BCrypt's default: a deliberate balance between login latency and the cost
        // of an offline attack on a stolen hash dump.
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public AuthenticationManager authenticationManager(AppUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // Always run the password hash, even for an unknown email, so response timing does not
        // leak whether an account exists.
        provider.setHideUserNotFoundExceptions(true);
        return provider::authenticate;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.cors().allowedOrigins());
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setExposedHeaders(List.of("Location"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
