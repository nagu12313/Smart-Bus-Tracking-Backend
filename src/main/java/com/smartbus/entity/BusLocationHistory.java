package com.smartbus.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * BusLocationHistory — Cold append-only breadcrumb logs for path trails and safety audit.
 */
@Entity
@Table(name = "bus_location_history", indexes = {
    @Index(name = "idx_blh_bus_time", columnList = "bus_id, gps_timestamp DESC"),
    @Index(name = "idx_blh_trip",     columnList = "trip_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BusLocationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bus_id", nullable = false)
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

    @Column(name = "gps_timestamp", nullable = false)
    private LocalDateTime gpsTimestamp;

    @Column(name = "received_at", nullable = false)
    @Builder.Default
    private LocalDateTime receivedAt = LocalDateTime.now();
}
