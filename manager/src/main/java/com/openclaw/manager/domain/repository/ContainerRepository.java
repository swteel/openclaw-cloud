package com.openclaw.manager.domain.repository;

import com.openclaw.manager.domain.entity.Container;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ContainerRepository extends JpaRepository<Container, Long> {
    Optional<Container> findByUserId(Long userId);

    @Query("SELECT c FROM Container c JOIN User u ON c.userId = u.id " +
           "WHERE c.status = 'RUNNING' AND u.lastActiveAt < :threshold AND u.isDeleted = 0")
    List<Container> findRunningInactiveContainers(@Param("threshold") LocalDateTime threshold);

    @Query("SELECT c FROM Container c JOIN User u ON c.userId = u.id " +
           "WHERE c.status = 'STOPPED' AND u.lastActiveAt < :threshold AND u.isDeleted = 0")
    List<Container> findStoppedInactiveContainers(@Param("threshold") LocalDateTime threshold);

    long countByStatus(String status);
}
