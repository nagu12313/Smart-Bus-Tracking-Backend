package com.smartbus.service.impl;

import com.smartbus.dto.response.LocationResponse;
import com.smartbus.entity.*;
import com.smartbus.repository.NotificationRepository;
import com.smartbus.repository.RouteStopRepository;
import com.smartbus.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransportKnowledgeService {

    private final TrackingService trackingService;
    private final RouteStopRepository routeStopRepository;
    private final TripRepository tripRepository;
    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public String buildFactualKnowledge(TransportChatSecurityService.UserTransportContext ctx, Bus targetBus) {
        if (targetBus == null) {
            return buildFactualKnowledge(ctx);
        }
        TransportChatSecurityService.UserTransportContext singleBusCtx = TransportChatSecurityService.UserTransportContext.builder()
                .userId(ctx.getUserId())
                .userName(ctx.getUserName())
                .email(ctx.getEmail())
                .role(ctx.getRole())
                .isAdmin(ctx.isAdmin())
                .driverProfile(ctx.getDriverProfile())
                .authorizedStudents(ctx.getAuthorizedStudents())
                .authorizedBuses(List.of(targetBus))
                .build();
        return buildFactualKnowledge(singleBusCtx);
    }

    @Transactional(readOnly = true)
    public String buildFactualKnowledge(TransportChatSecurityService.UserTransportContext ctx) {
        StringBuilder sb = new StringBuilder();

        sb.append("AUTHENTICATED USER DETAILS:\n");
        sb.append("- Name: ").append(ctx.getUserName() != null ? ctx.getUserName() : "User").append("\n");
        sb.append("- Role: ").append(ctx.getRole() != null ? ctx.getRole().name() : "USER").append("\n");
        sb.append("- Email: ").append(ctx.getEmail()).append("\n\n");

        if (!ctx.getAuthorizedStudents().isEmpty()) {
            sb.append("AUTHORIZED STUDENTS LINKED TO THIS USER:\n");
            for (Student s : ctx.getAuthorizedStudents()) {
                sb.append("• Student: ").append(s.getUser() != null ? s.getUser().getName() : "Student")
                        .append(" | Roll: ").append(s.getRollNumber())
                        .append(" | Class: ").append(s.getClassName()).append(" ").append(s.getSection() != null ? s.getSection() : "")
                        .append(" | Bus: ").append(s.getBus() != null ? s.getBus().getBusNumber() : "None")
                        .append(" | Pickup Stop: ").append(s.getPickupStop() != null ? s.getPickupStop().getName() : "Unassigned")
                        .append(" | Drop Stop: ").append(s.getDropStop() != null ? s.getDropStop().getName() : "Unassigned")
                        .append("\n");
            }
            sb.append("\n");
        }

        if (ctx.getDriverProfile() != null) {
            Driver d = ctx.getDriverProfile();
            sb.append("DRIVER PROFILE:\n");
            sb.append("- Driver Name: ").append(d.getUser() != null ? d.getUser().getName() : ctx.getUserName()).append("\n");
            sb.append("- Phone: ").append(d.getPhone()).append("\n");
            sb.append("- License: ").append(d.getLicenseNumber()).append("\n");
            sb.append("- Status: ").append(d.getStatus()).append("\n\n");
        }

        if (ctx.getAuthorizedBuses().isEmpty()) {
            sb.append("BUS ASSIGNMENTS:\n");
            sb.append("No campus bus is currently assigned to this account.\n\n");
        } else {
            sb.append("AUTHORIZED BUS VEHICLES & LIVE TELEMETRY:\n");
            for (Bus bus : ctx.getAuthorizedBuses()) {
                sb.append("• Bus Number: ").append(bus.getBusNumber())
                        .append(" (").append(bus.getBusName()).append(")")
                        .append("\n  - Capacity: ").append(bus.getCapacity()).append(" seats")
                        .append("\n  - Operational Status: ").append(bus.getStatus());

                // Driver info
                if (bus.getDriver() != null) {
                    Driver d = bus.getDriver();
                    String driverName = d.getUser() != null ? d.getUser().getName() : "Assigned Driver";
                    sb.append("\n  - Assigned Driver: ").append(driverName)
                            .append(" (Phone: ").append(d.getPhone()).append(", License: ").append(d.getLicenseNumber()).append(")");
                } else {
                    sb.append("\n  - Assigned Driver: None currently assigned");
                }

                // Route info
                if (bus.getRoute() != null) {
                    Route r = bus.getRoute();
                    sb.append("\n  - Assigned Route: ").append(r.getName())
                            .append(" (From: ").append(r.getStartPoint()).append(" To: ").append(r.getEndPoint()).append(")")
                            .append("\n  - Distance: ").append(r.getDistance() != null ? r.getDistance() : "N/A")
                            .append("\n  - Scheduled Duration: ").append(r.getDurationMinutes()).append(" mins");

                    // Stops list
                    try {
                        List<RouteStop> stops = routeStopRepository.findByRouteIdWithStop(r.getId());
                        if (!stops.isEmpty()) {
                            sb.append("\n  - Route Stops:");
                            for (RouteStop rs : stops) {
                                sb.append("\n    Stop ").append(rs.getStopOrder()).append(": ")
                                        .append(rs.getStop().getName())
                                        .append(" (Scheduled: ").append(rs.getArrivalTime() != null ? rs.getArrivalTime() : "TBD").append(")");
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Could not fetch route stops: {}", e.getMessage());
                    }
                } else {
                    sb.append("\n  - Assigned Route: No route assigned yet");
                }

                // Live GPS Location & Freshness
                try {
                    LocationResponse loc = trackingService.getLatestLocation(bus.getId());
                    if (loc != null && loc.getLatitude() != null && loc.getLongitude() != null) {
                        double lat = loc.getLatitude();
                        double lng = loc.getLongitude();
                        double spd = loc.getSpeed() != null ? loc.getSpeed() : 0.0;
                        boolean moving = spd > 2.0;
                        String mapsUrl = String.format(java.util.Locale.US, "https://www.google.com/maps?q=%.6f,%.6f", lat, lng);
                        String directionsUrl = String.format(java.util.Locale.US, "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f", lat, lng);

                        sb.append("\n  - Live GPS Telemetry:")
                                .append("\n    • Latitude: ").append(lat)
                                .append("\n    • Longitude: ").append(lng)
                                .append("\n    • Verified Location: ").append(loc.getLocationName() != null ? loc.getLocationName() : "On Route")
                                .append("\n    • Nearby Landmark: ").append(loc.getNearbyLandmark() != null ? loc.getNearbyLandmark() : "None")
                                .append("\n    • Road Alignment: ").append(Boolean.TRUE.equals(loc.getIsRoadMatched()) ? "Road Matched (" + (loc.getRoadName() != null ? loc.getRoadName() : "Mapped Road") + ")" : "Direct GPS")
                                .append("\n    • GPS Accuracy: ").append(loc.getAccuracyDescription() != null ? loc.getAccuracyDescription() : "GPS Fix")
                                .append("\n    • Speed: ").append(spd).append(" km/h")
                                .append("\n    • Movement Status: ").append(moving ? "MOVING (" + spd + " km/h)" : "STOPPED / AT STOP")
                                .append("\n    • Heading: ").append(loc.getHeading() != null ? loc.getHeading() : 0.0).append("°")
                                .append("\n    • GPS Freshness: ").append(Boolean.TRUE.equals(loc.getIsStale()) ? "STALE / DISCONNECTED" : "LIVE & ACTIVE")
                                .append("\n    • Last Update: ").append(loc.getGpsTime() != null ? loc.getGpsTime() : loc.getReceivedAt())
                                .append("\n    • Google Maps Location Link: ").append(mapsUrl)
                                .append("\n    • Google Maps Directions Link: ").append(directionsUrl);

                        // If student is linked with pickup stop, compute dynamic ETA
                        for (Student s : ctx.getAuthorizedStudents()) {
                            if (s.getPickupStop() != null && s.getPickupStop().getLatitude() != null && s.getPickupStop().getLongitude() != null) {
                                double distKm = haversineKm(lat, lng, s.getPickupStop().getLatitude(), s.getPickupStop().getLongitude());
                                int etaMins = Math.max(1, (int) Math.round((distKm / 25.0) * 60.0));
                                sb.append(String.format(java.util.Locale.US, "\n    • Calculated ETA to %s's Pickup Stop (%s): ~%d mins (distance: %.2f km)",
                                        s.getUser() != null ? s.getUser().getName() : "Student",
                                        s.getPickupStop().getName(), etaMins, distKm));
                            }
                        }
                    } else {
                        sb.append("\n  - Live GPS: No GPS signal received yet (Device Offline)");
                    }
                } catch (Exception e) {
                    sb.append("\n  - Live GPS: Device currently offline");
                }

                // Active Trip info
                try {
                    tripRepository.findTopByBusIdAndStatusOrderByCreatedAtDesc(bus.getId(), "IN_PROGRESS")
                            .ifPresent(trip -> {
                                sb.append("\n  - Active Trip: Trip #").append(trip.getId())
                                        .append(" (Started at: ").append(trip.getStartTime()).append(")")
                                        .append(", Status: IN_PROGRESS");
                            });
                } catch (Exception e) {
                    log.debug("No active trip found: {}", e.getMessage());
                }

                sb.append("\n\n");
            }
        }

        // Recent Notifications for the user
        if (ctx.getUserId() != null) {
            try {
                List<Notification> notifs = notificationRepository
                        .findByUserIdOrderByCreatedAtDesc(ctx.getUserId(), PageRequest.of(0, 3))
                        .getContent();
                if (!notifs.isEmpty()) {
                    sb.append("RECENT NOTIFICATIONS:\n");
                    for (Notification n : notifs) {
                        sb.append("• [").append(n.getType()).append("] ").append(n.getTitle()).append(": ").append(n.getMessage()).append("\n");
                    }
                    sb.append("\n");
                }
            } catch (Exception e) {
                log.debug("Could not fetch notifications: {}", e.getMessage());
            }
        }

        return sb.toString();
    }

    public double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }
}
