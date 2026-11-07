package com.smartbus.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * RouteStop — junction between Route and Stop.
 * Defines the stop order and scheduled arrival time.
 */
@Entity
@Table(name = "route_stops",
       uniqueConstraints = @UniqueConstraint(columnNames = {"route_id","stop_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RouteStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "stop_id", nullable = false)
    private Stop stop;

    @Column(name = "stop_order", nullable = false)
    private Integer stopOrder;

    @Column(name = "arrival_time", length = 10)
    private String arrivalTime;
}
