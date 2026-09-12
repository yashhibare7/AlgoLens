package com.algolens.service;

import com.algolens.dto.auth.AuthResponse;
import com.algolens.dto.auth.LoginRequest;
import com.algolens.dto.auth.RegisterRequest;
import com.algolens.dto.auth.UserResponse;
import com.algolens.entity.Role;
import com.algolens.entity.User;
import com.algolens.exception.ConflictException;
import com.algolens.exception.NotFoundException;
import com.algolens.repository.UserRepository;
import com.algolens.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CreditService creditService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager, JwtService jwtService,
            CreditService creditService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.creditService = creditService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with that email already exists");
        }

        User user = users.save(new User(request.name().trim(), email,
                passwordEncoder.encode(request.password()), Role.USER, 0));
        creditService.grantSignupBonus(user);

        log.info("Registered user {}", user.getId());
        return token(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        // Delegating to the AuthenticationManager keeps password checking, timing-attack
        // mitigation and account-state rules in Spring Security rather than reimplemented here.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));

        User user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new NotFoundException("Account not found"));
        return token(user);
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(Long userId) {
        return users.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> NotFoundException.of("User", userId));
    }

    private AuthResponse token(User user) {
        String jwt = jwtService.issue(user.getId(), user.getEmail(), user.getName());
        return AuthResponse.bearer(jwt, jwtService.expiresInSeconds(), UserResponse.from(user));
    }
}
