package com.smartbus.repository;

import com.smartbus.entity.RouteStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RouteStopRepository extends JpaRepository<RouteStop, Long> {
    List<RouteStop> findByRouteIdOrderByStopOrderAsc(Long routeId);

    @Query("SELECT rs FROM RouteStop rs JOIN FETCH rs.stop WHERE rs.route.id = :routeId ORDER BY rs.stopOrder ASC")
    List<RouteStop> findByRouteIdWithStop(@Param("routeId") Long routeId);

    @Query("SELECT rs FROM RouteStop rs JOIN FETCH rs.stop JOIN FETCH rs.route ORDER BY rs.route.id ASC, rs.stopOrder ASC")
    List<RouteStop> findAllWithStop();

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RouteStop rs WHERE rs.route.id = :routeId")
    void deleteByRouteId(@Param("routeId") Long routeId);
}
