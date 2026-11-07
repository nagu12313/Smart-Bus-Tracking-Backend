package com.smartbus.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Trip entity — one bus journey (morning/evening run).
 * Trip lifecycle: SCHEDULED → IN_PROGRESS → COMPLETED / CANCELLED
 */
@Entity
@Table(name = "trips", indexes = {
    @Index(name = "idx_trips_bus",    columnList = "bus_id"),
    @Index(name = "idx_trips_status", columnList = "status"),
    @Index(name = "idx_trips_date",   columnList = "start_time")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "bus_id", nullable = false)
    private Bus bus;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    /** MORNING or EVENING */
    @Column(name = "trip_type", nullable = false, length = 20)
    @Builder.Default
    private String tripType = "MORNING";

    /** SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "SCHEDULED";

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
