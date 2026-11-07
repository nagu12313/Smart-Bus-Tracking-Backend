package com.smartbus.repository;

import com.smartbus.entity.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RouteRepository extends JpaRepository<Route, Long> {
    List<Route> findByIsActiveTrue();
    boolean existsByName(String name);
}
