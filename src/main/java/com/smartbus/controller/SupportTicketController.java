package com.smartbus.controller;

import com.smartbus.dto.request.*;
import com.smartbus.dto.response.ApiResponse;
import com.smartbus.dto.response.SupportMessageResponse;
import com.smartbus.dto.response.SupportTicketResponse;
import com.smartbus.entity.Role;
import com.smartbus.entity.TicketCategory;
import com.smartbus.entity.TicketPriority;
import com.smartbus.entity.TicketStatus;
import com.smartbus.entity.User;
import com.smartbus.exception.BadRequestException;
import com.smartbus.exception.ResourceNotFoundException;
import com.smartbus.repository.UserRepository;
import com.smartbus.service.SupportTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for Support Ticket System (Helpdesk).
 * Serves both user ticket submissions (parents, students, drivers)
 * and administrator management functions (assigning, resolving, replying).
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Support Tickets", description = "Helpdesk, ticketing, and administrative issue resolution")
@SecurityRequirement(name = "bearerAuth")
public class SupportTicketController {

    private final SupportTicketService ticketService;
    private final UserRepository userRepository;
    private final com.smartbus.service.impl.EmailService emailService;

    private User getAuthenticatedUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new BadRequestException("Unauthenticated request");
        }
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userDetails.getUsername()));
    }

    private boolean isUserAdmin(User user) {
        return user.getRole() == Role.ADMIN || user.getRole() == Role.SCHOOL_ADMIN;
    }

    // ─────────────────────────────────────────────────────────────
    // USER & COMMON ENDPOINTS (/api/support/tickets)
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/api/support/tickets")
    @Operation(summary = "Submit a new support ticket")
    public ResponseEntity<ApiResponse<SupportTicketResponse>> createTicket(
            @Valid @RequestBody CreateTicketRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);
        SupportTicketResponse response = ticketService.createTicket(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Support ticket submitted successfully", response));
    }

    @GetMapping("/api/support/tickets/my")
    @Operation(summary = "Get tickets submitted by authenticated user")
    public ResponseEntity<ApiResponse<List<SupportTicketResponse>>> getMyTickets(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);
        List<SupportTicketResponse> tickets = ticketService.getMyTicketsList(user.getId());
        return ResponseEntity.ok(ApiResponse.success(tickets));
    }

    @GetMapping("/api/support/tickets/{ticketId}")
    @Operation(summary = "Get support ticket details and conversation thread")
    public ResponseEntity<ApiResponse<SupportTicketResponse>> getTicketDetails(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);
        boolean isAdmin = isUserAdmin(user);
        SupportTicketResponse response = ticketService.getTicketDetails(ticketId, user.getId(), isAdmin);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/api/support/tickets/{ticketId}/messages")
    @Operation(summary = "Post a message or reply to a support ticket thread")
    public ResponseEntity<ApiResponse<SupportMessageResponse>> postMessage(
            @PathVariable Long ticketId,
            @Valid @RequestBody CreateTicketMessageRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);
        boolean isAdmin = isUserAdmin(user);
        SupportMessageResponse response = ticketService.addMessage(ticketId, user.getId(), request, isAdmin);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Message sent successfully", response));
    }

    @GetMapping("/api/support/tickets/{ticketId}/messages")
    @Operation(summary = "Get conversation messages for a ticket")
    public ResponseEntity<ApiResponse<List<SupportMessageResponse>>> getMessages(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);
        boolean isAdmin = isUserAdmin(user);
        List<SupportMessageResponse> messages = ticketService.getMessages(ticketId, user.getId(), isAdmin);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    // ─────────────────────────────────────────────────────────────
    // ADMINISTRATOR ENDPOINTS (/api/admin/support/tickets)
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/api/admin/support/tickets")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHOOL_ADMIN')")
    @Operation(summary = "Get all tickets for administrative management with filters")
    public ResponseEntity<ApiResponse<Page<SupportTicketResponse>>> getAllTicketsAdmin(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) TicketCategory category,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        Page<SupportTicketResponse> tickets = ticketService.getAllTicketsAdmin(status, priority, category, search, pageable);
        return ResponseEntity.ok(ApiResponse.success(tickets));
    }

    @PatchMapping("/api/admin/support/tickets/{ticketId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHOOL_ADMIN')")
    @Operation(summary = "Update support ticket status (e.g. RESOLVED, CLOSED, IN_PROGRESS)")
    public ResponseEntity<ApiResponse<SupportTicketResponse>> updateStatus(
            @PathVariable Long ticketId,
            @Valid @RequestBody UpdateTicketStatusRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User admin = getAuthenticatedUser(userDetails);
        SupportTicketResponse response = ticketService.updateStatus(ticketId, admin.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Ticket status updated to " + request.getStatus(), response));
    }

    @PatchMapping("/api/admin/support/tickets/{ticketId}/priority")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHOOL_ADMIN')")
    @Operation(summary = "Update support ticket priority (LOW, MEDIUM, HIGH, URGENT)")
    public ResponseEntity<ApiResponse<SupportTicketResponse>> updatePriority(
            @PathVariable Long ticketId,
            @Valid @RequestBody UpdateTicketPriorityRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User admin = getAuthenticatedUser(userDetails);
        SupportTicketResponse response = ticketService.updatePriority(ticketId, admin.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Ticket priority updated to " + request.getPriority(), response));
    }

    @PatchMapping("/api/admin/support/tickets/{ticketId}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHOOL_ADMIN')")
    @Operation(summary = "Assign support ticket to a transport staff user")
    public ResponseEntity<ApiResponse<SupportTicketResponse>> assignTicket(
            @PathVariable Long ticketId,
            @Valid @RequestBody AssignTicketRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User admin = getAuthenticatedUser(userDetails);
        SupportTicketResponse response = ticketService.assignTicket(ticketId, admin.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Ticket assigned successfully", response));
    }

    @PostMapping("/api/admin/support/tickets/{ticketId}/reply")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHOOL_ADMIN')")
    @Operation(summary = "Administrator reply to a user ticket")
    public ResponseEntity<ApiResponse<SupportMessageResponse>> adminReply(
            @PathVariable Long ticketId,
            @Valid @RequestBody CreateTicketMessageRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User admin = getAuthenticatedUser(userDetails);
        SupportMessageResponse response = ticketService.addMessage(ticketId, admin.getId(), request, true);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Administrator response posted successfully", response));
    }

    @PostMapping("/api/admin/support/tickets/email-report")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHOOL_ADMIN')")
    @Operation(summary = "Email support tickets summary report to administrator")
    public ResponseEntity<ApiResponse<String>> sendEmailReport(
            @RequestParam(defaultValue = "bollanagu6868@gmail.com") String email) {
        org.springframework.data.domain.Page<SupportTicketResponse> page =
                ticketService.getAllTicketsAdmin(null, null, null, null, org.springframework.data.domain.PageRequest.of(0, 50));
        emailService.sendAdminTicketsReport(email, page.getContent());
        return ResponseEntity.ok(ApiResponse.success("Admin tickets report emailed successfully to " + email, email));
    }
}
