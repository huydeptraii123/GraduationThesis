package com.slatcut.cutting.service;

import com.slatcut.cutting.dto.LoginRequest;
import com.slatcut.cutting.dto.LoginResponse;
import com.slatcut.cutting.repository.UserRepository;
import com.slatcut.cutting.security.JwtProperties;
import com.slatcut.cutting.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtService jwtService,
            JwtProperties jwtProperties) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        var user = userRepository.findByUsername(request.getUsername()).orElseThrow();
        String roleCode = user.getRole().getCode();
        String token = jwtService.generateToken(user.getUsername(), roleCode);

        return new LoginResponse(token, user.getUsername(), roleCode, jwtProperties.getExpirationMs());
    }
}
