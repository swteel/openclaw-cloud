package com.openclaw.manager.domain.repository;

import com.openclaw.manager.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsernameAndIsDeleted(String username, int isDeleted);
    boolean existsByUsername(String username);
    Optional<User> findByGatewayToken(String gatewayToken);
}
