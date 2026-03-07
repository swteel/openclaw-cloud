package com.openclaw.manager.service;

import com.openclaw.manager.domain.entity.User;
import com.openclaw.manager.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class UserActivityService {

    private final UserRepository userRepo;

    public UserActivityService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @Transactional
    public void recordHeartbeat(Long userId) {
        userRepo.findById(userId).ifPresent(user -> {
            user.setLastActiveAt(LocalDateTime.now());
            userRepo.save(user);
        });
    }
}
