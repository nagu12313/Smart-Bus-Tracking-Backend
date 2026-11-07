package com.smartbus.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * BusLatestLocation — Hot table with exactly 1 row per bus for instant O(1) live map lookups.
 */
@Entity
@Table(name = "bus_latest_locations", indexes = {
    @Index(name = "idx_bll_bus_status", columnList = "bus_id, status"),
    @Index(name = "idx_bll_timestamp",  columnList = "gps_timestamp")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BusLatestLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "bus_id", nullable = false, unique = true)
    private Bus bus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private GpsDevice device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column
    @Builder.Default
    private Double speed = 0.0;

    @Column
    private Double heading;

    @Column
    @Builder.Default
    private Double accuracy = 5.0;

    @Column
    @Builder.Default
    private Double altitude = 0.0;

    @Column(name = "ignition_on")
    @Builder.Default
    private Boolean ignitionOn = true;

    @Column(name = "gps_timestamp", nullable = false)
    private LocalDateTime gpsTimestamp;

    @Column(name = "received_at", nullable = false)
    @Builder.Default
    private LocalDateTime receivedAt = LocalDateTime.now();

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ONLINE"; // ONLINE, ON_ROUTE, AT_STOP, OFFLINE
}
