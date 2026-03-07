package com.openclaw.manager.domain.repository;

import com.openclaw.manager.domain.entity.PortAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PortAllocationRepository extends JpaRepository<PortAllocation, Integer> {
    @Query("SELECT p.port FROM PortAllocation p ORDER BY p.port ASC")
    List<Integer> findAllAllocatedPorts();

    void deleteByUserId(Long userId);
}
