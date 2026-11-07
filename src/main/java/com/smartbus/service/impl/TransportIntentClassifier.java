package com.smartbus.service.impl;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class TransportIntentClassifier {

    public enum Intent {
        GREETING,
        BUS_LOCATION,
        BUS_SCHEDULE,
        DRIVER_INFO,
        ETA_ARRIVAL,
        ROUTE_STOPS,
        TRIP_STATUS,
        BUS_DELAY,
        NOTIFICATIONS,
        UNSUPPORTED_ASSISTANCE,
        UNRELATED
    }

    private static final Pattern PROMPT_INJECTION_PATTERN = Pattern.compile(
            "(?i)\\b(ignore previous instructions|system prompt|jailbreak|disregard all|act as|dan mode|bypass|as an ai language model|reveal key|reveal secret)\\b"
    );

    private static boolean hasWord(String text, String... words) {
        for (String w : words) {
            if (Pattern.compile("\\b" + Pattern.quote(w) + "\\b", Pattern.CASE_INSENSITIVE).matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    public boolean isPromptInjection(String input) {
        if (input == null) return false;
        return PROMPT_INJECTION_PATTERN.matcher(input).find();
    }

    public Intent classify(String message) {
        if (message == null || message.trim().isEmpty()) {
            return Intent.UNRELATED;
        }

        String q = message.toLowerCase(Locale.ENGLISH).trim();

        // 1. Check prompt injection
        if (isPromptInjection(q)) {
            return Intent.UNRELATED;
        }

        // 2. Unsupported assistance (ticket, fee, refund, complaint, administrative change)
        if (hasWord(q, "ticket", "tickets", "refund", "refunds", "fee", "fees", "pay", "payment",
                "fine", "fines", "complaint", "complaints", "admission", "discipline",
                "cancel", "withdraw", "renewal")) {
            return Intent.UNSUPPORTED_ASSISTANCE;
        }

        // 3. Greeting
        if (q.matches("^(hi|hello|hey|good morning|good afternoon|good evening|greetings)\\b.*")) {
            return Intent.GREETING;
        }

        // 4. Transportation check: must contain at least one transit concept (word-level)
        boolean isTransportRelated = hasWord(q,
                "bus", "buses", "van", "shuttle", "driver", "drivers", "route", "routes",
                "stop", "stops", "pickup", "pickups", "drop", "drops", "trip", "trips",
                "gps", "eta", "tracking", "track", "delay", "delays", "delayed", "speed",
                "location", "arrive", "arrival", "timing", "timings", "schedule", "schedules",
                "timetable", "alert", "alerts", "late", "moving", "vehicle", "transit");

        if (!isTransportRelated) {
            return Intent.UNRELATED;
        }

        // 5. Categorize specific transportation intents
        if (hasWord(q, "where", "location", "moving", "speed", "track", "tracking", "position") ||
            q.contains("live map")) {
            return Intent.BUS_LOCATION;
        }

        if (hasWord(q, "eta", "arrive", "arrival", "reaching", "reach") ||
            q.contains("when will") || q.contains("how long") || q.contains("how many minutes")) {
            return Intent.ETA_ARRIVAL;
        }

        if (hasWord(q, "driver", "drivers", "driving") ||
            q.contains("who is driving") || q.contains("driver phone") || q.contains("driver contact")) {
            return Intent.DRIVER_INFO;
        }

        if (hasWord(q, "delay", "delays", "delayed", "late", "stuck") || q.contains("traffic jam")) {
            return Intent.BUS_DELAY;
        }

        if (hasWord(q, "schedule", "schedules", "timing", "timings", "timetable", "shift", "shifts") ||
            q.contains("morning") || q.contains("evening") || q.contains("bus time")) {
            return Intent.BUS_SCHEDULE;
        }

        if (hasWord(q, "stop", "stops", "route", "routes", "waypoint") ||
            q.contains("next stop") || q.contains("pickup point") || q.contains("drop point")) {
            return Intent.ROUTE_STOPS;
        }

        if (hasWord(q, "trip", "trips", "started", "active") || q.contains("trip status")) {
            return Intent.TRIP_STATUS;
        }

        if (hasWord(q, "notification", "notifications", "alert", "alerts", "announcement", "announcements")) {
            return Intent.NOTIFICATIONS;
        }

        return Intent.BUS_LOCATION;
    }
}
