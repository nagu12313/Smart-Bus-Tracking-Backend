package com.smartbus.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * BusRouteAssignment entity — formal assignment of a bus to a route and driver.
 */
@Entity
@Table(name = "bus_route_assignments", indexes = {
    @Index(name = "idx_bra_bus",    columnList = "bus_id"),
    @Index(name = "idx_bra_route",  columnList = "route_id"),
    @Index(name = "idx_bra_driver", columnList = "driver_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BusRouteAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "bus_id", nullable = false)
    private Bus bus;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @Column(name = "trip_type", nullable = false, length = 20)
    @Builder.Default
    private String tripType = "MORNING"; // MORNING, AFTERNOON, EVENING

    @Column(name = "start_time", nullable = false, length = 20)
    @Builder.Default
    private String startTime = "07:30 AM";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
