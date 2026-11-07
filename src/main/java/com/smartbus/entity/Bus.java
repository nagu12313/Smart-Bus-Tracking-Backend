package com.smartbus.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Bus entity — represents a school or college bus vehicle.
 */
@Entity
@Table(name = "buses", indexes = {
    @Index(name = "idx_buses_status", columnList = "status"),
    @Index(name = "idx_buses_driver", columnList = "driver_id"),
    @Index(name = "idx_buses_route",  columnList = "route_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Bus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bus_number", nullable = false, unique = true, length = 20)
    private String busNumber;

    @Column(name = "bus_name", nullable = false, length = 100)
    private String busName;

    @Column(nullable = false)
    private Integer capacity;

    /**
     * Status values: ONLINE, ON_ROUTE, AT_STOP, OFFLINE
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "OFFLINE";

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "route_id")
    private Route route;

    /** Unique ID of the GPS hardware device installed in this bus */
    @Column(name = "gps_device_id", length = 100)
    private String gpsDeviceId;

    /** Secret token the GPS device uses to authenticate API calls */
    @Column(name = "gps_device_token", length = 255)
    private String gpsDeviceToken;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
