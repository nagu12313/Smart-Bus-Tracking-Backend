package com.smartbus.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbus.dto.response.LocationResponse;
import com.smartbus.dto.response.TransportChatInitResponse;
import com.smartbus.dto.response.TransportChatMessageResponse;
import com.smartbus.entity.*;
import com.smartbus.repository.BusRepository;
import com.smartbus.repository.RouteStopRepository;
import com.smartbus.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransportChatService {

    private final TransportChatSecurityService securityService;
    private final TransportKnowledgeService knowledgeService;
    private final TransportIntentClassifier intentClassifier;
    private final TrackingService trackingService;
    private final RouteStopRepository routeStopRepository;
    private final BusRepository busRepository;
    private final StudentRepository studentRepository;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-3.5-flash-lite}")
    private String geminiModel;

    // In-memory conversation history per user
    private final Map<Long, List<TransportChatMessageResponse>> conversationHistory = new ConcurrentHashMap<>();

    // In-memory identified active bus per user session
    private final Map<Long, Bus> userActiveBus = new ConcurrentHashMap<>();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public TransportChatInitResponse getInitContext(User user) {
        TransportChatSecurityService.UserTransportContext ctx = securityService.getAuthorizedContext(user);
        Role role = ctx.getRole() != null ? ctx.getRole() : Role.PARENT;
        List<Bus> authorizedBuses = ctx.getAuthorizedBuses();
        Long userId = user != null ? user.getId() : 0L;

        // Auto-identify bus if single or driver/parent/student assigned
        Bus identifiedBus = userActiveBus.get(userId);
        if (identifiedBus == null && !authorizedBuses.isEmpty()) {
            identifiedBus = authorizedBuses.get(0);
            userActiveBus.put(userId, identifiedBus);
        }

        String welcome;
        List<String> questions = new ArrayList<>();
        String contextSummary;

        if (role == Role.ADMIN || role == Role.SCHOOL_ADMIN) {
            List<Bus> allBuses = busRepository.findAll();
            long activeCount = allBuses.stream().filter(b -> "ACTIVE".equalsIgnoreCase(b.getStatus())).count();
            long routeCount = allBuses.stream().map(Bus::getRoute).filter(Objects::nonNull).distinct().count();
            welcome = String.format("Welcome, Administrator %s! I am your Smart Transport AI Assistant. You have full administrative oversight of %d fleet vehicles (%d active) across %d routes. How can I assist you with fleet operations today?",
                    user != null && user.getName() != null ? user.getName() : "Admin", allBuses.size(), activeCount, routeCount);
            questions.add("Fleet status overview");
            questions.add("How many buses are active?");
            questions.add("Show moving vehicles");
            questions.add("List all routes");
            if (!allBuses.isEmpty()) {
                questions.add("Check Bus " + allBuses.get(0).getBusNumber());
            }
            contextSummary = String.format("Admin Overview • %d Vehicles • %d Active Routes", allBuses.size(), routeCount);
        } else if (role == Role.DRIVER) {
            String busNumber = identifiedBus != null ? identifiedBus.getBusNumber() : "Assigned Bus";
            String busName = identifiedBus != null ? identifiedBus.getBusName() : "Vehicle";
            String routeName = identifiedBus != null && identifiedBus.getRoute() != null ? identifiedBus.getRoute().getName() : "Main Campus";
            welcome = String.format("Hello, Driver %s! You are assigned to Bus %s (%s) on route '%s'. How can I help you with your schedule or stops today?",
                    user != null && user.getName() != null ? user.getName() : "Driver", busNumber, busName, routeName);
            questions.add("My assigned route & stops");
            questions.add("Students on my bus");
            questions.add("Next stop arrival time");
            questions.add("My vehicle status");
            contextSummary = String.format("Driver Portal • Bus %s • %s", busNumber, routeName);
        } else if (role == Role.STUDENT) {
            Student s = ctx.getAuthorizedStudents().isEmpty() ? null : ctx.getAuthorizedStudents().get(0);
            String busNumber = identifiedBus != null ? identifiedBus.getBusNumber() : "Campus Bus";
            String pickupStop = s != null && s.getPickupStop() != null ? s.getPickupStop().getName() : "your designated stop";
            welcome = String.format("Hello, %s! You are assigned to Bus %s with pickup at %s. Need your live bus location or arrival time?",
                    user != null && user.getName() != null ? user.getName() : "Student", busNumber, pickupStop);
            questions.add("Where is my bus right now?");
            questions.add("When will it reach " + (s != null && s.getPickupStop() != null ? s.getPickupStop().getName() : "my stop") + "?");
            questions.add("Who is my driver?");
            questions.add("Route stops list");
            contextSummary = String.format("Student Transit • Bus %s • Stop: %s", busNumber, pickupStop);
        } else { // PARENT
            Student s = ctx.getAuthorizedStudents().isEmpty() ? null : ctx.getAuthorizedStudents().get(0);
            String childName = s != null && s.getUser() != null ? s.getUser().getName() : "your child";
            String busNumber = identifiedBus != null ? identifiedBus.getBusNumber() : "Campus Bus";
            String pickupStop = s != null && s.getPickupStop() != null ? s.getPickupStop().getName() : "pickup point";
            welcome = String.format("Welcome, %s! Monitoring transportation for %s on Bus %s. How can I assist you today?",
                    user != null && user.getName() != null ? user.getName() : "Parent", childName, busNumber);
            questions.add("Where is my child's bus right now?");
            questions.add("When will it reach " + pickupStop + "?");
            questions.add("Driver name & phone number");
            questions.add("Is my bus moving or delayed?");
            contextSummary = String.format("Parent Guardian • %s • Bus %s", childName, busNumber);
        }

        return TransportChatInitResponse.builder()
                .welcomeMessage(welcome)
                .suggestedQuestions(questions)
                .userRole(role.name())
                .userName(user != null ? user.getName() : "User")
                .identifiedBusNumber(identifiedBus != null ? identifiedBus.getBusNumber() : null)
                .identifiedBusName(identifiedBus != null ? identifiedBus.getBusName() : null)
                .contextSummary(contextSummary)
                .build();
    }

    public TransportChatMessageResponse processMessage(User user, String userMessage) {
        String cleanMessage = userMessage != null ? userMessage.trim() : "";
        Long userId = user != null ? user.getId() : 0L;

        // Store user message in history
        TransportChatMessageResponse userEntry = TransportChatMessageResponse.builder()
                .id(UUID.randomUUID().toString())
                .sender("user")
                .message(cleanMessage)
                .intent("USER_INPUT")
                .timestamp(LocalDateTime.now())
                .build();
        recordHistory(userId, userEntry);

        // 1. Intent Classification
        TransportIntentClassifier.Intent intent = intentClassifier.classify(cleanMessage);

        String botReply;

        // 2. Strict Topic Enforcement (Unsupported assistance like complaints/refunds)
        if (intent == TransportIntentClassifier.Intent.UNSUPPORTED_ASSISTANCE) {
            botReply = "Please contact your transport administrator for further assistance.";
        } else if (intent == TransportIntentClassifier.Intent.FLEET_OVERVIEW) {
            TransportChatSecurityService.UserTransportContext ctx = securityService.getAuthorizedContext(user);
            botReply = generateFleetOverview(ctx);
        } else if (intent == TransportIntentClassifier.Intent.STUDENT_LIST) {
            TransportChatSecurityService.UserTransportContext ctx = securityService.getAuthorizedContext(user);
            Bus currentBus = userActiveBus.get(userId);
            if (currentBus == null && !ctx.getAuthorizedBuses().isEmpty()) {
                currentBus = ctx.getAuthorizedBuses().get(0);
                userActiveBus.put(userId, currentBus);
            }
            botReply = generateStudentList(ctx, currentBus);
        } else {
            // 3. Bus Identification and Authorization Check (Mandatory Behavior)
            TransportChatSecurityService.UserTransportContext ctx = securityService.getAuthorizedContext(user);
            List<Bus> authorizedBuses = ctx.getAuthorizedBuses();

            Bus matchedAuthorized = findBusInList(cleanMessage, authorizedBuses);
            Bus matchedAnyInDb = findBusInList(cleanMessage, busRepository.findAll());

            if (matchedAuthorized != null) {
                // Valid authorized bus identified or switched from this message (Rule 4 & 7)
                userActiveBus.put(userId, matchedAuthorized);
                if (isOnlyBusSpecification(cleanMessage, matchedAuthorized)) {
                    botReply = "Bus identified successfully. What would you like to know about this bus?";
                } else {
                    // User asked a question with the bus specified in the message
                    botReply = answerBusQuestion(cleanMessage, intent, matchedAuthorized, ctx);
                }
            } else if (matchedAnyInDb != null) {
                // Bus exists in DB, but user is NOT authorized to access it (Rule 4, 7, 10)
                botReply = String.format("Bus '%s' is not associated with your account. Please enter a valid bus name or number.",
                        matchedAnyInDb.getBusNumber());
            } else if (isAttemptingToSpecifyBus(cleanMessage)) {
                // Invalid bus name/number entered (Rule 6)
                botReply = "Invalid bus name or number. Please enter a valid bus name or bus number.";
            } else if (intent == TransportIntentClassifier.Intent.UNRELATED) {
                // Strictly unrelated non-bus query
                botReply = "I'm your Smart Transport Assistant. I can help you with bus schedules, live tracking, drivers, routes, pickup timings, and trip information. Please ask a transportation-related question.";
            } else if (authorizedBuses.isEmpty()) {
                botReply = "No bus is currently assigned to your account. Please contact your transport administrator.";
            } else {
                // Check if bus is already identified for this session or single assigned (Rule 1 & 8)
                Bus currentBus = userActiveBus.get(userId);
                // For Driver, Student, or Parent, always auto-select their single/first assigned bus
                if (currentBus == null && (!ctx.isAdmin() || authorizedBuses.size() == 1)) {
                    currentBus = authorizedBuses.get(0);
                    userActiveBus.put(userId, currentBus);
                }

                if (currentBus == null && ctx.isAdmin()) {
                    // For Admin with multiple buses asking general questions without specifying a bus
                    if (intent == TransportIntentClassifier.Intent.GREETING) {
                        botReply = String.format("Welcome, Administrator %s! You are overseeing %d fleet vehicles. Ask me for a 'Fleet overview' or specify a bus number for live tracking.",
                                ctx.getUserName() != null ? ctx.getUserName() : "Admin", authorizedBuses.size());
                    } else {
                        botReply = generateFleetOverview(ctx);
                    }
                } else if (currentBus == null) {
                    if (intent == TransportIntentClassifier.Intent.GREETING) {
                        botReply = "Welcome to Smart Transport Assistant! Which bus would you like assistance with? Please enter your bus name or bus number.";
                    } else {
                        botReply = "Which bus would you like to know about? Please enter your bus name or bus number.";
                    }
                } else {
                    // Bus is already identified! (Rule 5)
                    if (intent == TransportIntentClassifier.Intent.GREETING) {
                        botReply = String.format("Welcome to Smart Transport Assistant! You are connected to Bus %s (%s). What would you like to know about this bus?",
                                currentBus.getBusNumber(), currentBus.getBusName());
                    } else {
                        // Answer question for currentBus
                        botReply = answerBusQuestion(cleanMessage, intent, currentBus, ctx);
                    }
                }
            }
        }

        TransportChatMessageResponse botEntry = TransportChatMessageResponse.builder()
                .id(UUID.randomUUID().toString())
                .sender("assistant")
                .message(botReply)
                .intent(intent.name())
                .timestamp(LocalDateTime.now())
                .build();
        recordHistory(userId, botEntry);

        return botEntry;
    }

    private String answerBusQuestion(
            String userQuery,
            TransportIntentClassifier.Intent intent,
            Bus targetBus,
            TransportChatSecurityService.UserTransportContext ctx
    ) {
        String aiAnswer = callGeminiApi(userQuery, targetBus, ctx);
        if (aiAnswer != null && !aiAnswer.isBlank()) {
            return aiAnswer;
        }
        return generateDeterministicReply(intent, userQuery, targetBus, ctx);
    }

    public List<TransportChatMessageResponse> getHistory(User user) {
        Long userId = user != null ? user.getId() : 0L;
        return conversationHistory.getOrDefault(userId, new ArrayList<>());
    }

    public void clearHistory(User user) {
        Long userId = user != null ? user.getId() : 0L;
        conversationHistory.remove(userId);
        userActiveBus.remove(userId);
    }

    private void recordHistory(Long userId, TransportChatMessageResponse msg) {
        conversationHistory.computeIfAbsent(userId, k -> Collections.synchronizedList(new ArrayList<>()));
        List<TransportChatMessageResponse> list = conversationHistory.get(userId);
        list.add(msg);
        if (list.size() > 50) {
            list.remove(0);
        }
    }

    private String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private Bus findBusInList(String query, List<Bus> buses) {
        if (query == null || query.isBlank() || buses == null || buses.isEmpty()) {
            return null;
        }
        String clean = query.toLowerCase().trim();
        String normQuery = normalize(query);

        for (Bus b : buses) {
            String num = b.getBusNumber() != null ? b.getBusNumber().toLowerCase().trim() : "";
            String name = b.getBusName() != null ? b.getBusName().toLowerCase().trim() : "";
            String normNum = normalize(num);
            String normName = normalize(name);

            // Match full normalized bus number (e.g. "ap07ab1234" in "ap 07 ab 1234" or "ap07-ab-1234")
            if (!normNum.isBlank() && normQuery.contains(normNum)) {
                return b;
            }
            // Match bus name (e.g. "campus express 01" or "madhapur" or "guntur")
            if (!normName.isBlank() && normQuery.contains(normName)) {
                return b;
            }
            // Match without leading zero: e.g. "campus express 1" vs "campus express 01"
            String normNameNoZero = normName.replace("01", "1").replace("02", "2").replace("03", "3");
            String normQueryNoZero = normQuery.replace("01", "1").replace("02", "2").replace("03", "3");
            if (!normNameNoZero.isBlank() && normQueryNoZero.contains(normNameNoZero)) {
                return b;
            }
            // Match ID: "bus 1", "bus #1"
            if (b.getId() != null && clean.matches(".*\\bbus\\s*#?\\s*" + b.getId() + "\\b.*")) {
                return b;
            }
            // Match last 4 digits of number if query has standalone digits (e.g. "1234")
            if (normNum.length() >= 4) {
                String last4 = normNum.substring(normNum.length() - 4);
                if (clean.matches(".*\\b" + last4 + "\\b.*")) {
                    return b;
                }
            }
        }
        return null;
    }

    private boolean isAttemptingToSpecifyBus(String query) {
        if (query == null) return false;
        String q = query.toLowerCase().trim();
        return q.matches(".*\\b(bus|vehicle)\\s+[a-z0-9#-]+.*") ||
               q.matches(".*[a-z]{2}\\s*[0-9]{2}\\s*[a-z0-9 -]{2,10}.*");
    }

    private boolean isOnlyBusSpecification(String query, Bus bus) {
        if (query == null) return false;
        String q = query.toLowerCase().trim();
        if (bus.getBusNumber() != null) {
            q = q.replace(bus.getBusNumber().toLowerCase(), "");
            q = q.replace(normalize(bus.getBusNumber()), "");
        }
        if (bus.getBusName() != null) {
            q = q.replace(bus.getBusName().toLowerCase(), "");
            q = q.replace(normalize(bus.getBusName()), "");
        }
        // Remove common filler words
        q = q.replaceAll("\\b(bus|number|name|hi|hello|hey|check|select|the|details|please|info|#)\\b", "");
        q = q.replaceAll("[^a-z0-9]", "");
        return q.length() <= 3;
    }

    private String callGeminiApi(
            String userMessage,
            Bus targetBus,
            TransportChatSecurityService.UserTransportContext ctx
    ) {
        if (geminiApiKey == null || geminiApiKey.isBlank() || targetBus == null) {
            return null;
        }

        try {
            String factualKnowledge = knowledgeService.buildFactualKnowledge(ctx, targetBus);

            String systemPrompt = "You are the 'Smart Transport Assistant' for a school/college bus tracking system.\n" +
                    "CRITICAL RULES:\n" +
                    "1. The active identified bus is: Bus " + targetBus.getBusNumber() + " (" + targetBus.getBusName() + "). Focus exclusively on this bus.\n" +
                    "2. Base your answers STRICTLY on the VERIFIED REAL-TIME TRANSPORT CONTEXT below. Do not make up coordinates, drivers, or ETAs.\n" +
                    "3. When answering questions about bus location, tracking, whereabouts, movement, or ETA, ALWAYS provide the exact bus number, speed, moving/stopped status, GPS update time, and the clickable Google Maps links in this exact format:\n" +
                    "   [📍 Open in Google Maps](https://www.google.com/maps?q=LAT,LNG)\n" +
                    "   [🧭 Get Directions to Bus](https://www.google.com/maps/dir/?api=1&destination=LAT,LNG)\n" +
                    "4. If GPS is stale or offline, clearly state the last recorded update time without inventing a location.\n" +
                    "5. If the user asks about an issue you cannot solve (such as refund, fee, complaint, fine), respond EXACTLY: 'Please contact your transport administrator for further assistance.'\n" +
                    "6. If the question is completely unrelated to transportation, respond EXACTLY: 'I'm your Smart Transport Assistant. I can help you with bus schedules, live tracking, drivers, routes, pickup timings, and trip information. Please ask a transportation-related question.'\n" +
                    "7. Keep responses concise, direct, helpful, and polite. Avoid unnecessary fluff.\n\n" +
                    "VERIFIED REAL-TIME TRANSPORT CONTEXT:\n" + factualKnowledge;

            Map<String, Object> contentsPart = Map.of(
                    "contents", List.of(
                            Map.of(
                                    "role", "user",
                                    "parts", List.of(
                                            Map.of("text", systemPrompt + "\n\nUSER QUESTION: " + userMessage)
                                    )
                            )
                    ),
                    "generationConfig", Map.of(
                            "temperature", 0.2,
                            "maxOutputTokens", 500
                    )
            );

            String requestJson = objectMapper.writeValueAsString(contentsPart);
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + geminiModel + ":generateContent?key=" + geminiApiKey;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(12))
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode candidates = root.path("candidates");
                if (candidates.isArray() && !candidates.isEmpty()) {
                    JsonNode parts = candidates.get(0).path("content").path("parts");
                    if (parts.isArray()) {
                        for (JsonNode part : parts) {
                            JsonNode textNode = part.path("text");
                            if (!textNode.isMissingNode() && !textNode.asText().isBlank()) {
                                return textNode.asText().trim();
                            }
                        }
                    }
                }
            } else {
                log.warn("Gemini API returned status {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("Gemini AI call failed, falling back to deterministic engine: {}", e.getMessage());
        }

        return null;
    }

    private String generateDeterministicReply(
            TransportIntentClassifier.Intent intent,
            String userQuery,
            Bus targetBus,
            TransportChatSecurityService.UserTransportContext ctx
    ) {
        if (targetBus == null) {
            return "Which bus would you like to know about? Please enter your bus name or bus number.";
        }

        LocationResponse loc = null;
        try {
            loc = trackingService.getLatestLocation(targetBus.getId());
        } catch (Exception ignored) {}

        boolean hasGps = loc != null && loc.getLatitude() != null && loc.getLongitude() != null;
        double lat = hasGps ? loc.getLatitude() : 0.0;
        double lng = hasGps ? loc.getLongitude() : 0.0;
        double speed = (hasGps && loc.getSpeed() != null) ? loc.getSpeed() : 0.0;
        boolean isMoving = speed > 2.0;
        boolean isStale = hasGps && Boolean.TRUE.equals(loc.getIsStale());
        String mapsUrl = hasGps ? String.format(Locale.US, "https://www.google.com/maps?q=%.6f,%.6f", lat, lng) : "";
        String dirUrl = hasGps ? String.format(Locale.US, "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f", lat, lng) : "";
        String updateTime = (hasGps && loc.getGpsTime() != null) ? loc.getGpsTime().toString().replace("T", " ") : "Recently";

        // Dynamic ETA calculation
        String etaText = "Calculating based on road route";
        if (hasGps && !ctx.getAuthorizedStudents().isEmpty()) {
            Student s = ctx.getAuthorizedStudents().get(0);
            if (s.getPickupStop() != null && s.getPickupStop().getLatitude() != null && s.getPickupStop().getLongitude() != null) {
                double dist = knowledgeService.haversineKm(lat, lng, s.getPickupStop().getLatitude(), s.getPickupStop().getLongitude());
                int mins = Math.max(1, (int) Math.round((dist / 25.0) * 60.0));
                etaText = String.format(Locale.US, "~%d mins (%.1f km away at %s)", mins, dist, s.getPickupStop().getName());
            }
        } else if (targetBus.getRoute() != null && targetBus.getRoute().getDurationMinutes() != null) {
            etaText = String.format(Locale.US, "~%d mins total route transit time", targetBus.getRoute().getDurationMinutes());
        }

        switch (intent) {
            case BUS_LOCATION:
                if (hasGps) {
                    String placeInfo = loc.getLocationName() != null ? "\n• Verified Place: " + loc.getLocationName() : "";
                    String landmarkInfo = loc.getNearbyLandmark() != null ? "\n• Nearby: " + loc.getNearbyLandmark() : "";
                    String roadInfo = Boolean.TRUE.equals(loc.getIsRoadMatched()) ? " (Road Aligned)" : "";
                    return String.format(Locale.US,
                            "🚌 Bus %s (%s)%s%s\n" +
                            "• Movement: %s (Speed: %.1f km/h%s)\n" +
                            "• Telemetry: %s (Updated: %s)\n" +
                            "• ETA: %s\n" +
                            "• Coordinates: %.6f, %.6f\n\n" +
                            "📍 [Open in Google Maps](%s)\n" +
                            "🧭 [Get Directions to Bus](%s)",
                            targetBus.getBusNumber(), targetBus.getBusName(),
                            placeInfo, landmarkInfo,
                            isMoving ? "Moving" : "Stopped / At Stop", speed, roadInfo,
                            isStale ? "STALE / DISCONNECTED" : "LIVE & ACTIVE",
                            updateTime, etaText, lat, lng, mapsUrl, dirUrl
                    );
                } else {
                    return String.format("Bus %s (%s) GPS device is currently offline. No live coordinates available.",
                            targetBus.getBusNumber(), targetBus.getBusName());
                }

            case ETA_ARRIVAL:
                if (hasGps) {
                    return String.format(Locale.US,
                            "⏱️ Dynamic ETA for Bus %s: %s.\n" +
                            "Current status: %s (Speed: %.1f km/h).\n\n" +
                            "📍 [Open in Google Maps](%s)",
                            targetBus.getBusNumber(), etaText,
                            isMoving ? "In transit" : "Stopped", speed, mapsUrl
                    );
                } else {
                    return String.format("Bus %s scheduled transit time is %d minutes. GPS telemetry is currently offline.",
                            targetBus.getBusNumber(),
                            targetBus.getRoute() != null ? targetBus.getRoute().getDurationMinutes() : 30);
                }

            case DRIVER_INFO:
                if (targetBus.getDriver() != null) {
                    Driver d = targetBus.getDriver();
                    String dName = d.getUser() != null ? d.getUser().getName() : "Assigned Driver";
                    String driverMsg = String.format("The assigned driver for Bus %s is %s (Phone: %s, License: %s).",
                            targetBus.getBusNumber(), dName, d.getPhone(), d.getLicenseNumber());
                    if (hasGps) {
                        driverMsg += String.format(Locale.US, "\n\n📍 [Open in Google Maps](%s)", mapsUrl);
                    }
                    return driverMsg;
                } else {
                    return String.format("Bus %s currently has no driver assigned in the system.", targetBus.getBusNumber());
                }

            case BUS_SCHEDULE:
                if (targetBus.getRoute() != null) {
                    return String.format("Bus %s operates on route '%s' (%s to %s) with a scheduled transit duration of %d minutes.",
                            targetBus.getBusNumber(), targetBus.getRoute().getName(),
                            targetBus.getRoute().getStartPoint(), targetBus.getRoute().getEndPoint(),
                            targetBus.getRoute().getDurationMinutes());
                } else {
                    return String.format("Bus %s operates standard campus pickup (07:30 AM – 08:30 AM) and evening drop (04:30 PM – 05:30 PM).",
                            targetBus.getBusNumber());
                }

            case ROUTE_STOPS:
                if (targetBus.getRoute() != null) {
                    try {
                        List<RouteStop> stops = routeStopRepository.findByRouteIdWithStop(targetBus.getRoute().getId());
                        if (!stops.isEmpty()) {
                            StringBuilder rsb = new StringBuilder();
                            rsb.append(String.format("Bus %s is assigned to route '%s'.\nRoute Stops:\n",
                                    targetBus.getBusNumber(), targetBus.getRoute().getName()));
                            for (RouteStop rs : stops) {
                                rsb.append(String.format("• Stop %d: %s (%s)\n",
                                        rs.getStopOrder(), rs.getStop().getName(),
                                        rs.getArrivalTime() != null ? rs.getArrivalTime() : "Scheduled"));
                            }
                            if (hasGps) {
                                rsb.append(String.format(Locale.US, "\n📍 [Open in Google Maps](%s)", mapsUrl));
                            }
                            return rsb.toString();
                        }
                    } catch (Exception ignored) {}
                    return String.format("Bus %s is assigned to route '%s' (From %s to %s).",
                            targetBus.getBusNumber(), targetBus.getRoute().getName(),
                            targetBus.getRoute().getStartPoint(), targetBus.getRoute().getEndPoint());
                } else {
                    return String.format("Bus %s has no active route assigned. Please contact your transport administrator.", targetBus.getBusNumber());
                }

            case BUS_DELAY:
                if (hasGps) {
                    return String.format(Locale.US,
                            "Bus %s status: %s (Speed: %.1f km/h). Telemetry is %s.\nETA: %s.\n\n📍 [Open in Google Maps](%s)",
                            targetBus.getBusNumber(),
                            isMoving ? "In transit along road route" : "Currently stopped",
                            speed,
                            isStale ? "stale/offline" : "live & active",
                            etaText, mapsUrl
                    );
                } else {
                    return String.format("Bus %s is monitored by campus GPS telemetry. If a delay occurs, an alert will be sent.",
                            targetBus.getBusNumber());
                }

            case TRIP_STATUS:
                String tripMsg = String.format("Bus %s operational status: %s (Movement: %s).",
                        targetBus.getBusNumber(), targetBus.getStatus(), isMoving ? "Moving" : "Stopped");
                if (hasGps) {
                    tripMsg += String.format(Locale.US, "\n\n📍 [Open in Google Maps](%s)", mapsUrl);
                }
                return tripMsg;

            default:
                return String.format("What would you like to know about Bus %s (%s)? Ask me about its live location, ETA, driver, route, or schedule.",
                        targetBus.getBusNumber(), targetBus.getBusName());
        }
    }

    private String generateFleetOverview(TransportChatSecurityService.UserTransportContext ctx) {
        List<Bus> allBuses = busRepository.findAll();
        if (allBuses.isEmpty()) {
            return "No buses are currently registered in the campus transport system.";
        }

        int totalBuses = allBuses.size();
        int movingCount = 0;
        int stoppedCount = 0;
        int parkedCount = 0;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("🚌 Campus Fleet Overview (%d Registered Vehicles):\n\n", totalBuses));

        for (Bus b : allBuses) {
            LocationResponse loc = null;
            try {
                loc = trackingService.getLatestLocation(b.getId());
            } catch (Exception ignored) {}

            boolean hasGps = loc != null && loc.getLatitude() != null && loc.getLongitude() != null;
            double speed = (hasGps && loc.getSpeed() != null) ? loc.getSpeed() : 0.0;
            String status;
            if (hasGps && speed > 2.0) {
                status = String.format("Moving (%.1f km/h)", speed);
                movingCount++;
            } else if (hasGps && "ACTIVE".equalsIgnoreCase(b.getStatus())) {
                status = "Stopped at stop / Idle";
                stoppedCount++;
            } else {
                status = "Parked / Inactive";
                parkedCount++;
            }

            String driverName = b.getDriver() != null && b.getDriver().getUser() != null ? b.getDriver().getUser().getName() : "Unassigned";
            String routeName = b.getRoute() != null ? b.getRoute().getName() : "No Route";
            sb.append(String.format("• Bus %s (%s) — %s | Route: %s | Driver: %s\n",
                    b.getBusNumber(), b.getBusName() != null ? b.getBusName() : "Bus",
                    status, routeName, driverName));
        }

        sb.append(String.format("\n📊 Live Telemetry Summary: %d Moving in transit • %d Stopped/Active • %d Parked\n\n",
                movingCount, stoppedCount, parkedCount));
        if (!allBuses.isEmpty()) {
            sb.append("Tip: Specify any bus number (e.g., 'Status of Bus ").append(allBuses.get(0).getBusNumber()).append("') for live coordinates, ETA, and Google Maps tracking.");
        }
        return sb.toString();
    }

    private String generateStudentList(TransportChatSecurityService.UserTransportContext ctx, Bus targetBus) {
        Role role = ctx.getRole();
        if (role == Role.PARENT || role == Role.STUDENT) {
            List<Student> students = ctx.getAuthorizedStudents();
            if (students.isEmpty()) {
                return "No student transport profile is currently linked to your account. Please contact your transport administrator.";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("🎓 Student Transportation Profile:\n\n");
            for (Student s : students) {
                String sName = s.getUser() != null ? s.getUser().getName() : "Student";
                String busNum = s.getBus() != null ? s.getBus().getBusNumber() : "None";
                String pickup = s.getPickupStop() != null ? s.getPickupStop().getName() : "Unassigned";
                String drop = s.getDropStop() != null ? s.getDropStop().getName() : "Unassigned";
                sb.append(String.format("• %s (Roll: %s, Class: %s)\n  - Assigned Bus: %s\n  - Pickup Stop: %s\n  - Drop Stop: %s\n",
                        sName, s.getRollNumber(), s.getClassName() != null ? s.getClassName() : "N/A",
                        busNum, pickup, drop));
            }
            return sb.toString();
        }

        // For Driver or Admin
        if (targetBus == null && !ctx.getAuthorizedBuses().isEmpty()) {
            targetBus = ctx.getAuthorizedBuses().get(0);
        }

        if (targetBus != null) {
            List<Student> busStudents = studentRepository.findByBusId(targetBus.getId());
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("👥 Student Passengers on Bus %s (%d Enrolled):\n\n",
                    targetBus.getBusNumber(), busStudents.size()));
            if (busStudents.isEmpty()) {
                sb.append("No students are currently assigned to this vehicle.");
            } else {
                for (Student s : busStudents) {
                    String sName = s.getUser() != null ? s.getUser().getName() : "Student";
                    String stop = s.getPickupStop() != null ? s.getPickupStop().getName() : "Standard Pickup";
                    sb.append(String.format("• %s (Class: %s) — Pickup Stop: %s\n",
                            sName, s.getClassName() != null ? s.getClassName() : "General", stop));
                }
            }
            return sb.toString();
        }

        long totalStudents = studentRepository.count();
        return String.format("A total of %d students are registered in the campus transport system. Ask about a specific bus to see its passenger roster.",
                totalStudents);
    }
}
