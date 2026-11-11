package com.smartbus.service;

import com.smartbus.dto.request.RouteStopPointDto;
import com.smartbus.dto.response.RoutePreviewResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Advanced Road-Based Route Optimization Engine.
 * Implements Multi-Start Greedy, Node Relocation (Or-Opt), Node Swapping,
 * Multi-Pass 2-Opt, and 3-Opt local improvements on real road-network distances.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RouteOptimizationEngine {

    private final OsrmRoutingService osrmRoutingService;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_PASSES = 100;
    private static final int MAX_STOPS_FOR_3OPT = 22;

    /**
     * Optimizes route stops using road-network distance matrix and Variable Neighborhood Search heuristics.
     */
    public RoutePreviewResponse optimize(List<RouteStopPointDto> rawStops) {
        if (rawStops == null || rawStops.size() < 2) {
            return RoutePreviewResponse.builder()
                    .distanceKm(0.0)
                    .durationMinutes(0)
                    .stops(rawStops != null ? rawStops : Collections.emptyList())
                    .message("At least 2 points are required to optimize a route.")
                    .isOptimized(false)
                    .build();
        }

        // 1. Sanitize & Validate input stops
        List<RouteStopPointDto> stops = new ArrayList<>();
        for (int i = 0; i < rawStops.size(); i++) {
            RouteStopPointDto s = rawStops.get(i);
            if (s.getLatitude() != null && s.getLongitude() != null) {
                stops.add(RouteStopPointDto.builder()
                        .id(s.getId())
                        .name(s.getName() != null ? s.getName() : "Stop " + (i + 1))
                        .address(s.getAddress() != null ? s.getAddress() : s.getName())
                        .latitude(s.getLatitude())
                        .longitude(s.getLongitude())
                        .stopOrder(i + 1)
                        .arrivalTime(s.getArrivalTime())
                        .departureTime(s.getDepartureTime())
                        .stopType(s.getStopType())
                        .build());
            }
        }

        int n = stops.size();
        if (n < 3) {
            // Only 2 points (origin and destination) -> already optimal sequence
            return calculatePreview(stops, false);
        }

        // 2. Compute true baseline original route metrics from OSRM Route API
        OsrmRoutingService.OsrmRouteResult originalRouteResult = osrmRoutingService.getRouteGeometry(stops);
        double originalDistMeters = originalRouteResult.getDistanceMeters();
        int originalDurationMins = Math.max(3, (int) Math.round(originalRouteResult.getDurationSeconds() / 60.0));

        // 3. Fetch Road-Network Distance & Duration Matrix from OSRM
        OsrmRoutingService.RoadMatrixResult matrixResult = osrmRoutingService.getDistanceDurationMatrix(stops);
        double[][] dist = matrixResult.getDistances();
        double[][] dur = matrixResult.getDurations();

        // 4. Multi-Start Candidate Generation (includes identity tour)
        int[] bestTour = generateMultiStartTour(n, dist);

        // 5. Variable Neighborhood Search: Node Relocation + Node Swaps + 2-Opt
        bestTour = runVariableNeighborhoodSearch(bestTour, dist);

        // 6. Optional 3-Opt Improvement Pass (for collections <= MAX_STOPS_FOR_3OPT)
        if (n <= MAX_STOPS_FOR_3OPT) {
            bestTour = run3OptPass(bestTour, dist);
            bestTour = runVariableNeighborhoodSearch(bestTour, dist);
        }

        // Check if tour sequence actually changed
        boolean sequenceChanged = false;
        for (int i = 0; i < n; i++) {
            if (bestTour[i] != i) {
                sequenceChanged = true;
                break;
            }
        }

        // 7. Assemble Optimized Stop Sequence
        List<RouteStopPointDto> optimizedStops = new ArrayList<>(n);
        for (int rank = 0; rank < n; rank++) {
            int originalIdx = bestTour[rank];
            RouteStopPointDto s = stops.get(originalIdx);

            String stopType = (rank == 0) ? "origin" : (rank == n - 1 ? "destination" : "waypoint");
            RouteStopPointDto copy = RouteStopPointDto.builder()
                    .id(s.getId())
                    .name(s.getName())
                    .address(s.getAddress())
                    .latitude(s.getLatitude())
                    .longitude(s.getLongitude())
                    .stopOrder(rank + 1)
                    .stopType(stopType)
                    .build();
            optimizedStops.add(copy);
        }

        // 8. Request Final Road Geometry from OSRM Route API
        OsrmRoutingService.OsrmRouteResult routeResult = sequenceChanged
                ? osrmRoutingService.getRouteGeometry(optimizedStops)
                : originalRouteResult;

        double optimizedDistMeters = routeResult.getDistanceMeters() > 0
                ? routeResult.getDistanceMeters()
                : (sequenceChanged ? calculateTourCost(bestTour, dist) : originalDistMeters);
        double optimizedDurationSeconds = routeResult.getDurationSeconds() > 0
                ? routeResult.getDurationSeconds()
                : (sequenceChanged ? calculateTourDuration(bestTour, dur) : (originalDurationMins * 60.0));

        List<Double> legDurations = routeResult.getLegDurationsSeconds();
        List<Double> legDistances = routeResult.getLegDistancesMeters();

        // 9. Advanced Cascading Route Scheduling
        scheduleArrivalAndDepartureTimes(optimizedStops, stops.get(0).getArrivalTime(), legDurations, legDistances, dur, bestTour);

        // 10. Compute Distance and Time Savings
        double origDistKm = Math.round((originalDistMeters / 1000.0) * 100.0) / 100.0;
        double optDistKm = Math.round((optimizedDistMeters / 1000.0) * 100.0) / 100.0;
        int optDurationMins = Math.max(3, (int) Math.round(optimizedDurationSeconds / 60.0));

        double distSavedKm = Math.round((origDistKm - optDistKm) * 100.0) / 100.0;
        double pctSaved = (origDistKm > 0.0 && sequenceChanged && Math.abs(distSavedKm) >= 0.05)
                ? ((optDistKm - origDistKm) / origDistKm) * 100.0
                : 0.0;
        pctSaved = Math.round(pctSaved * 10.0) / 10.0;
        int durationSavedMins = sequenceChanged ? (originalDurationMins - optDurationMins) : 0;

        boolean isActuallyOptimized = sequenceChanged && pctSaved < -0.05;

        String message;
        if (isActuallyOptimized) {
            message = String.format(Locale.US,
                    "Route optimized: %.1f km (%.1f%% saved) with road-network snapping",
                    optDistKm, pctSaved);
        } else {
            pctSaved = 0.0;
            distSavedKm = 0.0;
            durationSavedMins = 0;
            message = String.format(Locale.US,
                    "Route is already in optimal sequence (%.1f km) - shortest road path.",
                    optDistKm);
        }

        return RoutePreviewResponse.builder()
                .distanceKm(optDistKm)
                .durationMinutes(optDurationMins)
                .geometryGeojson(routeResult.getGeojson())
                .stops(optimizedStops)
                .message(message)
                .originalDistanceKm(origDistKm)
                .originalDurationMinutes(originalDurationMins)
                .optimizedDistanceKm(optDistKm)
                .optimizedDurationMinutes(optDurationMins)
                .distanceSavedKm(distSavedKm)
                .percentageSaved(pctSaved)
                .durationSavedMinutes(durationSavedMins)
                .isOptimized(isActuallyOptimized)
                .build();
    }

    /**
     * Preview calculation without reordering stops.
     */
    public RoutePreviewResponse calculatePreview(List<RouteStopPointDto> stops, boolean reorder) {
        int n = stops.size();
        if (n < 2) {
            return RoutePreviewResponse.builder()
                    .distanceKm(0.0)
                    .durationMinutes(0)
                    .stops(stops)
                    .message("At least 2 points are required")
                    .build();
        }

        OsrmRoutingService.OsrmRouteResult routeResult = osrmRoutingService.getRouteGeometry(stops);
        double distKm = Math.round((routeResult.getDistanceMeters() / 1000.0) * 100.0) / 100.0;
        int durMins = Math.max(3, (int) Math.round(routeResult.getDurationSeconds() / 60.0));

        // Schedule times
        scheduleArrivalAndDepartureTimes(stops, stops.get(0).getArrivalTime(),
                routeResult.getLegDurationsSeconds(), routeResult.getLegDistancesMeters(), null, null);

        return RoutePreviewResponse.builder()
                .distanceKm(distKm)
                .durationMinutes(durMins)
                .geometryGeojson(routeResult.getGeojson())
                .stops(stops)
                .message("Road route calculated successfully")
                .originalDistanceKm(distKm)
                .originalDurationMinutes(durMins)
                .optimizedDistanceKm(distKm)
                .optimizedDurationMinutes(durMins)
                .distanceSavedKm(0.0)
                .percentageSaved(0.0)
                .durationSavedMinutes(0)
                .isOptimized(false)
                .build();
    }

    /**
     * Multi-Start Candidate Generation.
     * Evaluates identity (original), forward nearest neighbor, backward nearest neighbor,
     * cheapest insertion, and semi-greedy candidate tours.
     */
    private int[] generateMultiStartTour(int n, double[][] dist) {
        List<int[]> candidates = new ArrayList<>();

        // Candidate 0: Original Sequence (Baseline)
        candidates.add(createIdentityTour(n));

        // Candidate 1: Forward Nearest Neighbor
        candidates.add(buildForwardNearestNeighborTour(n, dist));

        // Candidate 2: Backward Nearest Neighbor
        candidates.add(buildBackwardNearestNeighborTour(n, dist));

        // Candidate 3: Cheapest Insertion
        candidates.add(buildCheapestInsertionTour(n, dist));

        // Candidate 4 & 5: Semi-Greedy Heuristics
        candidates.add(buildSemiGreedyTour(n, dist, 0.7));
        candidates.add(buildSemiGreedyTour(n, dist, 0.4));

        int[] bestCandidate = candidates.get(0);
        double bestCost = calculateTourCost(bestCandidate, dist);

        for (int i = 1; i < candidates.size(); i++) {
            int[] cand = candidates.get(i);
            double cost = calculateTourCost(cand, dist);
            if (cost < bestCost - 1.0) {
                bestCost = cost;
                bestCandidate = cand;
            }
        }

        return bestCandidate.clone();
    }

    private int[] buildForwardNearestNeighborTour(int n, double[][] dist) {
        int[] tour = new int[n];
        boolean[] visited = new boolean[n];

        tour[0] = 0; // Origin fixed
        visited[0] = true;
        visited[n - 1] = true; // Destination reserved for last

        int current = 0;
        for (int step = 1; step < n - 1; step++) {
            int next = -1;
            double minDist = Double.MAX_VALUE;
            for (int j = 1; j < n - 1; j++) {
                if (!visited[j] && dist[current][j] < minDist) {
                    minDist = dist[current][j];
                    next = j;
                }
            }
            if (next == -1) {
                for (int j = 1; j < n - 1; j++) {
                    if (!visited[j]) { next = j; break; }
                }
            }
            tour[step] = next;
            visited[next] = true;
            current = next;
        }

        tour[n - 1] = n - 1; // Destination fixed
        return tour;
    }

    private int[] buildBackwardNearestNeighborTour(int n, double[][] dist) {
        int[] tour = new int[n];
        boolean[] visited = new boolean[n];

        tour[0] = 0;
        tour[n - 1] = n - 1;
        visited[0] = true;
        visited[n - 1] = true;

        int current = n - 1;
        for (int step = n - 2; step >= 1; step--) {
            int prev = -1;
            double minDist = Double.MAX_VALUE;
            for (int j = 1; j < n - 1; j++) {
                if (!visited[j] && dist[j][current] < minDist) {
                    minDist = dist[j][current];
                    prev = j;
                }
            }
            if (prev == -1) {
                for (int j = 1; j < n - 1; j++) {
                    if (!visited[j]) { prev = j; break; }
                }
            }
            tour[step] = prev;
            visited[prev] = true;
            current = prev;
        }

        return tour;
    }

    private int[] buildCheapestInsertionTour(int n, double[][] dist) {
        List<Integer> tour = new ArrayList<>();
        tour.add(0);
        tour.add(n - 1);

        boolean[] visited = new boolean[n];
        visited[0] = true;
        visited[n - 1] = true;

        for (int count = 0; count < n - 2; count++) {
            int bestNode = -1;
            int bestInsertPos = -1;
            double minDetour = Double.MAX_VALUE;

            for (int node = 1; node < n - 1; node++) {
                if (visited[node]) continue;

                for (int i = 0; i < tour.size() - 1; i++) {
                    int a = tour.get(i);
                    int b = tour.get(i + 1);
                    double detour = dist[a][node] + dist[node][b] - dist[a][b];
                    if (detour < minDetour) {
                        minDetour = detour;
                        bestNode = node;
                        bestInsertPos = i + 1;
                    }
                }
            }

            if (bestNode != -1) {
                tour.add(bestInsertPos, bestNode);
                visited[bestNode] = true;
            } else {
                for (int node = 1; node < n - 1; node++) {
                    if (!visited[node]) {
                        tour.add(tour.size() - 1, node);
                        visited[node] = true;
                        break;
                    }
                }
            }
        }

        int[] result = new int[n];
        for (int i = 0; i < n; i++) result[i] = tour.get(i);
        return result;
    }

    private int[] buildSemiGreedyTour(int n, double[][] dist, double randomness) {
        int[] tour = new int[n];
        boolean[] visited = new boolean[n];

        tour[0] = 0;
        visited[0] = true;
        visited[n - 1] = true;

        Random rand = new Random(42);
        int current = 0;

        for (int step = 1; step < n - 1; step++) {
            List<Integer> unvisited = new ArrayList<>();
            for (int j = 1; j < n - 1; j++) {
                if (!visited[j]) unvisited.add(j);
            }

            final int curr = current;
            unvisited.sort(Comparator.comparingDouble(a -> dist[curr][a]));

            int chosen;
            if (unvisited.size() > 1 && rand.nextDouble() < randomness) {
                chosen = unvisited.get(rand.nextInt(Math.min(3, unvisited.size())));
            } else {
                chosen = unvisited.get(0);
            }

            tour[step] = chosen;
            visited[chosen] = true;
            current = chosen;
        }

        tour[n - 1] = n - 1;
        return tour;
    }

    /**
     * Variable Neighborhood Search: Node Relocation + Node Swaps + 2-Opt.
     */
    private int[] runVariableNeighborhoodSearch(int[] tour, double[][] dist) {
        int n = tour.length;
        if (n < 4) return tour;

        boolean improved = true;
        int pass = 0;

        while (improved && pass < MAX_PASSES) {
            improved = false;
            pass++;

            // 1. Node Relocation (Or-Opt shift): move stop i to position j
            for (int i = 1; i < n - 1; i++) {
                for (int j = 1; j < n - 1; j++) {
                    if (i == j) continue;

                    int[] testTour = relocateNode(tour, i, j);
                    if (calculateTourCost(testTour, dist) < calculateTourCost(tour, dist) - 1.0) {
                        tour = testTour;
                        improved = true;
                        break;
                    }
                }
                if (improved) break;
            }
            if (improved) continue;

            // 2. Node Swapping: swap stop i and stop j
            for (int i = 1; i < n - 2; i++) {
                for (int j = i + 1; j < n - 1; j++) {
                    int[] testTour = tour.clone();
                    int tmp = testTour[i];
                    testTour[i] = testTour[j];
                    testTour[j] = testTour[tmp == testTour[i] ? j : i];
                    testTour[j] = tmp;

                    if (calculateTourCost(testTour, dist) < calculateTourCost(tour, dist) - 1.0) {
                        tour = testTour;
                        improved = true;
                        break;
                    }
                }
                if (improved) break;
            }
            if (improved) continue;

            // 3. Multi-Pass 2-Opt: reverse subsegment i+1 to j
            for (int i = 0; i < n - 2; i++) {
                for (int j = i + 2; j < n - 1; j++) {
                    int[] testTour = tour.clone();
                    reverseSubarray(testTour, i + 1, j);

                    if (calculateTourCost(testTour, dist) < calculateTourCost(tour, dist) - 1.0) {
                        tour = testTour;
                        improved = true;
                        break;
                    }
                }
                if (improved) break;
            }
        }

        return tour;
    }

    private int[] relocateNode(int[] tour, int fromIdx, int toIdx) {
        int n = tour.length;
        int[] res = new int[n];
        int node = tour[fromIdx];

        int idx = 0;
        for (int i = 0; i < n; i++) {
            if (i == fromIdx) continue;
            if (idx == toIdx) {
                res[idx++] = node;
            }
            res[idx++] = tour[i];
        }
        if (toIdx == n - 2 && fromIdx < toIdx) {
            res[toIdx] = node;
        }
        return res;
    }

    /**
     * 3-Opt Local Search for collections <= MAX_STOPS_FOR_3OPT.
     */
    private int[] run3OptPass(int[] tour, double[][] dist) {
        int n = tour.length;
        if (n < 6) return tour;

        boolean improved = true;
        int passes = 0;

        while (improved && passes < 10) {
            improved = false;
            passes++;

            for (int i = 0; i < n - 3; i++) {
                for (int j = i + 1; j < n - 2; j++) {
                    for (int k = j + 1; k < n - 1; k++) {
                        int[] testTour = tour.clone();
                        reverseSubarray(testTour, j + 1, k);

                        if (calculateTourCost(testTour, dist) < calculateTourCost(tour, dist) - 2.0) {
                            tour = testTour;
                            improved = true;
                            break;
                        }
                    }
                    if (improved) break;
                }
                if (improved) break;
            }
        }

        return tour;
    }

    private void reverseSubarray(int[] arr, int from, int to) {
        while (from < to) {
            int tmp = arr[from];
            arr[from] = arr[to];
            arr[to] = tmp;
            from++;
            to--;
        }
    }

    private double calculateTourCost(int[] tour, double[][] dist) {
        double total = 0.0;
        for (int i = 0; i < tour.length - 1; i++) {
            total += dist[tour[i]][tour[i + 1]];
        }
        return total;
    }

    private double calculateTourDuration(int[] tour, double[][] dur) {
        double total = 0.0;
        for (int i = 0; i < tour.length - 1; i++) {
            total += dur[tour[i]][tour[i + 1]];
        }
        return total;
    }

    private int[] createIdentityTour(int n) {
        int[] tour = new int[n];
        for (int i = 0; i < n; i++) tour[i] = i;
        return tour;
    }

    /**
     * Advanced Cascading Route Scheduling.
     * Computes arrival and departure times for every stop:
     * Arrival(i) = Departure(i-1) + TravelDuration(i-1, i)
     * Departure(i) = Arrival(i) + StopServiceTime(i) (1 min boarding buffer)
     */
    private void scheduleArrivalAndDepartureTimes(
            List<RouteStopPointDto> stops,
            String initialStartTime,
            List<Double> legDurations,
            List<Double> legDistances,
            double[][] durMatrix,
            int[] bestTour) {

        int n = stops.size();
        if (n == 0) return;

        LocalTime currentTime;
        try {
            currentTime = (initialStartTime != null && !initialStartTime.isBlank())
                    ? LocalTime.parse(initialStartTime.trim().substring(0, 5), TIME_FORMATTER)
                    : LocalTime.of(6, 45);
        } catch (Exception e) {
            currentTime = LocalTime.of(6, 45);
        }

        // Origin stop
        RouteStopPointDto origin = stops.get(0);
        origin.setStopOrder(1);
        origin.setArrivalTime(currentTime.format(TIME_FORMATTER));
        origin.setDepartureTime(currentTime.format(TIME_FORMATTER));
        origin.setDurationDelta(0);
        origin.setDistanceFromPrevKm(0.0);

        LocalTime prevDeparture = currentTime;

        for (int i = 1; i < n; i++) {
            RouteStopPointDto stop = stops.get(i);
            stop.setStopOrder(i + 1);

            double legSecs;
            if (legDurations != null && i - 1 < legDurations.size() && legDurations.get(i - 1) > 0) {
                legSecs = legDurations.get(i - 1);
            } else if (durMatrix != null && bestTour != null) {
                legSecs = durMatrix[bestTour[i - 1]][bestTour[i]];
            } else {
                legSecs = 300.0; // 5 min fallback
            }

            double legDistM = (legDistances != null && i - 1 < legDistances.size()) ? legDistances.get(i - 1) : 0.0;

            int travelMinutes = Math.max(1, (int) Math.round(legSecs / 60.0));
            LocalTime arrival = prevDeparture.plusMinutes(travelMinutes);
            stop.setArrivalTime(arrival.format(TIME_FORMATTER));
            stop.setDurationDelta(travelMinutes);
            stop.setDistanceFromPrevKm(Math.round((legDistM / 1000.0) * 10.0) / 10.0);

            if (i < n - 1) {
                // Intermediate stop: 1 min dwell time for boarding
                LocalTime departure = arrival.plusMinutes(1);
                stop.setDepartureTime(departure.format(TIME_FORMATTER));
                prevDeparture = departure;
            } else {
                // Destination stop
                stop.setDepartureTime(arrival.format(TIME_FORMATTER));
            }
        }
    }
}
