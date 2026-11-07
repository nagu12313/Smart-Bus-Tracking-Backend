package com.smartbus.repository;

import com.smartbus.entity.Stop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StopRepository extends JpaRepository<Stop, Long> {
    boolean existsByName(String name);
}
