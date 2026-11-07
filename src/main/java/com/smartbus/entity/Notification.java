package com.smartbus.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Notification — system notifications stored in DB.
 * Types: BUS_STARTED, APPROACHING, PICKED_UP, DROPPED_OFF, DELAYED, TRIP_COMPLETED, INFO
 */
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notif_user",   columnList = "user_id"),
    @Index(name = "idx_notif_unread", columnList = "user_id,is_read")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    /** INFO, BUS_STARTED, APPROACHING, PICKED_UP, DROPPED_OFF, DELAYED, TRIP_COMPLETED */
    @Column(nullable = false, length = 50)
    @Builder.Default
    private String type = "INFO";

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bus_id")
    private Bus bus;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
