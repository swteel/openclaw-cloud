package com.openclaw.manager.service;

import com.openclaw.manager.domain.entity.User;
import com.openclaw.manager.domain.repository.UserRepository;
import com.openclaw.manager.dto.RegisterRequest;
import com.openclaw.manager.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtProvider;
    private final ContainerLifecycleService containerService;

    public AuthService(UserRepository userRepo, PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtProvider, ContainerLifecycleService containerService) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.containerService = containerService;
    }

    @Transactional
    public User register(RegisterRequest req) {
        if (userRepo.existsByUsername(req.getUsername())) {
            throw new IllegalArgumentException("Username already exists: " + req.getUsername());
        }

        User user = new User();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setDashscopeKey(req.getDashscopeKey());
        user.setGatewayToken(UUID.randomUUID().toString().replace("-", ""));
        user.setRole("USER");
        user = userRepo.save(user);

        containerService.createAndStart(user);
        return user;
    }

    public String login(String username, String password) {
        User user = userRepo.findByUsernameAndIsDeleted(username, 0)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        return jwtProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
    }

    public String getGatewayToken(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return user.getGatewayToken();
    }

    public User verifyToken(String token) {
        if (!jwtProvider.validateToken(token)) {
            throw new IllegalArgumentException("Invalid token");
        }
        Long userId = jwtProvider.getUserId(token);
        return userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
