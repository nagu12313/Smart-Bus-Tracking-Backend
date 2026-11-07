package com.smartbus.service.impl;

import com.smartbus.dto.request.*;
import com.smartbus.dto.response.*;
import com.smartbus.entity.*;
import com.smartbus.exception.BadRequestException;
import com.smartbus.exception.ResourceNotFoundException;
import com.smartbus.repository.*;
import com.smartbus.service.SupportTicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupportTicketServiceImpl implements SupportTicketService {

    private final SupportTicketRepository ticketRepository;
    private final SupportMessageRepository messageRepository;
    private final SupportAttachmentRepository attachmentRepository;
    private final SupportTicketHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final BusRepository busRepository;
    private final TripRepository tripRepository;
    private final NotificationService notificationService;

    private static final AtomicLong COUNTER = new AtomicLong(System.currentTimeMillis() % 100000);

    @Override
    @Transactional
    public SupportTicketResponse createTicket(Long userId, CreateTicketRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Bus bus = null;
        if (request.getBusId() != null) {
            bus = busRepository.findById(request.getBusId()).orElse(null);
        }

        Trip trip = null;
        if (request.getTripId() != null) {
            trip = tripRepository.findById(request.getTripId()).orElse(null);
        }

        // Generate unique ticket number: TKT-YYYYMM-XXXX
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        long seq = COUNTER.incrementAndGet();
        String ticketNumber = String.format("TKT-%s-%04d", datePart, seq % 10000);

        SupportTicket ticket = SupportTicket.builder()
                .ticketNumber(ticketNumber)
                .user(user)
                .category(request.getCategory())
                .subject(request.getSubject().trim())
                .description(request.getDescription().trim())
                .status(TicketStatus.OPEN)
                .priority(request.getPriority() != null ? request.getPriority() : TicketPriority.MEDIUM)
                .bus(bus)
                .trip(trip)
                .build();

        ticket = ticketRepository.save(ticket);

        // Process attachments if any
        if (request.getAttachments() != null && !request.getAttachments().isEmpty()) {
            for (AttachmentDto attDto : request.getAttachments()) {
                if (attDto.getFileName() != null && !attDto.getFileName().isBlank()) {
                    // Basic file size safety check (max 10MB)
                    if (attDto.getFileSize() != null && attDto.getFileSize() > 10 * 1024 * 1024) {
                        throw new BadRequestException("Attachment size exceeds maximum allowed 10MB limit");
                    }
                    SupportAttachment attachment = SupportAttachment.builder()
                            .ticket(ticket)
                            .fileName(attDto.getFileName())
                            .fileType(attDto.getFileType() != null ? attDto.getFileType() : "application/octet-stream")
                            .fileSize(attDto.getFileSize() != null ? attDto.getFileSize() : 0L)
                            .fileData(attDto.getFileData())
                            .build();
                    attachmentRepository.save(attachment);
                    ticket.getAttachments().add(attachment);
                }
            }
        }

        // Add initial audit history
        SupportTicketHistory history = SupportTicketHistory.builder()
                .ticket(ticket)
                .changedBy(user)
                .action("CREATED")
                .oldValue(null)
                .newValue(TicketStatus.OPEN.name())
                .notes("Ticket created by " + user.getName() + " (" + user.getRole() + ")")
                .build();
        historyRepository.save(history);

        // Send system notification to administrators
        try {
            String notifMsg = String.format("New support ticket %s [%s]: %s submitted by %s",
                    ticket.getTicketNumber(), ticket.getCategory(), ticket.getSubject(), user.getName());
            notificationService.createSystemNotification(
                    "SUPPORT_TICKET",
                    "Support Ticket Created: " + ticket.getTicketNumber(),
                    notifMsg,
                    trip,
                    bus
            );
        } catch (Exception e) {
            log.warn("Failed to dispatch admin notification for ticket {}: {}", ticket.getTicketNumber(), e.getMessage());
        }

        log.info("Support ticket {} created successfully by user {} (ID: {})", ticketNumber, user.getEmail(), user.getId());
        return mapToResponse(ticket, true, true, true);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SupportTicketResponse> getMyTickets(Long userId, Pageable pageable) {
        return ticketRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(t -> mapToResponse(t, false, false, false));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupportTicketResponse> getMyTicketsList(Long userId) {
        return ticketRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(t -> mapToResponse(t, false, false, false))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public SupportTicketResponse getTicketDetails(Long ticketId, Long currentUserId, boolean isAdmin) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + ticketId));

        if (!isAdmin && !ticket.getUser().getId().equals(currentUserId)) {
            throw new BadRequestException("Unauthorized: You do not have permission to view this ticket");
        }

        return mapToResponse(ticket, true, true, true);
    }

    @Override
    @Transactional
    public SupportMessageResponse addMessage(Long ticketId, Long currentUserId, CreateTicketMessageRequest request, boolean isAdmin) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + ticketId));

        User sender = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + currentUserId));

        if (!isAdmin && !ticket.getUser().getId().equals(currentUserId)) {
            throw new BadRequestException("Unauthorized: You cannot post messages to this ticket");
        }

        SupportMessage message = SupportMessage.builder()
                .ticket(ticket)
                .sender(sender)
                .message(request.getMessage().trim())
                .isAdminReply(isAdmin)
                .build();

        message = messageRepository.save(message);

        // Process message attachments
        if (request.getAttachments() != null && !request.getAttachments().isEmpty()) {
            for (AttachmentDto attDto : request.getAttachments()) {
                if (attDto.getFileName() != null && !attDto.getFileName().isBlank()) {
                    SupportAttachment attachment = SupportAttachment.builder()
                            .ticket(ticket)
                            .message(message)
                            .fileName(attDto.getFileName())
                            .fileType(attDto.getFileType() != null ? attDto.getFileType() : "application/octet-stream")
                            .fileSize(attDto.getFileSize() != null ? attDto.getFileSize() : 0L)
                            .fileData(attDto.getFileData())
                            .build();
                    attachmentRepository.save(attachment);
                    message.getAttachments().add(attachment);
                }
            }
        }

        // Auto-update ticket status:
        // If admin replies -> WAITING_FOR_USER (if currently OPEN or IN_PROGRESS)
        // If user replies -> IN_PROGRESS (if currently WAITING_FOR_USER)
        TicketStatus oldStatus = ticket.getStatus();
        if (isAdmin && (ticket.getStatus() == TicketStatus.OPEN || ticket.getStatus() == TicketStatus.IN_PROGRESS)) {
            ticket.setStatus(TicketStatus.WAITING_FOR_USER);
            ticketRepository.save(ticket);
            logHistory(ticket, sender, "STATUS_CHANGED", oldStatus.name(), TicketStatus.WAITING_FOR_USER.name(), "Auto-updated on admin reply");
        } else if (!isAdmin && ticket.getStatus() == TicketStatus.WAITING_FOR_USER) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
            ticketRepository.save(ticket);
            logHistory(ticket, sender, "STATUS_CHANGED", oldStatus.name(), TicketStatus.IN_PROGRESS.name(), "Auto-updated on user reply");
        }

        // Notify counterpart
        try {
            if (isAdmin) {
                // Notify user that transport admin has responded
                notificationService.createNotificationForUser(
                        ticket.getUser(),
                        "TICKET_REPLY",
                        "New Response on Ticket " + ticket.getTicketNumber(),
                        "Administrator " + sender.getName() + " replied: " + truncate(request.getMessage(), 120),
                        ticket.getTrip(),
                        ticket.getBus()
                );
            } else {
                // Notify assigned admin or all admins
                String msg = String.format("User %s replied to ticket %s: %s",
                        sender.getName(), ticket.getTicketNumber(), truncate(request.getMessage(), 120));
                if (ticket.getAssignedTo() != null) {
                    notificationService.createNotificationForUser(
                            ticket.getAssignedTo(),
                            "TICKET_REPLY",
                            "User Reply on Ticket " + ticket.getTicketNumber(),
                            msg,
                            ticket.getTrip(),
                            ticket.getBus()
                    );
                } else {
                    notificationService.createSystemNotification(
                            "TICKET_REPLY",
                            "User Reply on Ticket " + ticket.getTicketNumber(),
                            msg,
                            ticket.getTrip(),
                            ticket.getBus()
                    );
                }
            }
        } catch (Exception e) {
            log.warn("Failed to dispatch reply notification: {}", e.getMessage());
        }

        return mapToMessageResponse(message);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupportMessageResponse> getMessages(Long ticketId, Long currentUserId, boolean isAdmin) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + ticketId));

        if (!isAdmin && !ticket.getUser().getId().equals(currentUserId)) {
            throw new BadRequestException("Unauthorized: You do not have permission to view messages for this ticket");
        }

        return messageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)
                .stream()
                .map(this::mapToMessageResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SupportTicketResponse> getAllTicketsAdmin(
            TicketStatus status,
            TicketPriority priority,
            TicketCategory category,
            String search,
            Pageable pageable
    ) {
        return ticketRepository.findWithFilters(status, priority, category, search, pageable)
                .map(t -> mapToResponse(t, false, false, false));
    }

    @Override
    @Transactional
    public SupportTicketResponse updateStatus(Long ticketId, Long adminUserId, UpdateTicketStatusRequest request) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + ticketId));

        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found with id: " + adminUserId));

        TicketStatus oldStatus = ticket.getStatus();
        TicketStatus newStatus = request.getStatus();

        if (oldStatus == newStatus) {
            return mapToResponse(ticket, true, true, true);
        }

        ticket.setStatus(newStatus);
        if (newStatus == TicketStatus.RESOLVED) {
            ticket.setResolvedAt(LocalDateTime.now());
        } else if (newStatus == TicketStatus.CLOSED) {
            ticket.setClosedAt(LocalDateTime.now());
        } else {
            ticket.setResolvedAt(null);
            ticket.setClosedAt(null);
        }

        ticket = ticketRepository.save(ticket);

        logHistory(ticket, admin, "STATUS_CHANGED", oldStatus.name(), newStatus.name(), request.getNotes());

        // Notify user about status change
        try {
            notificationService.createNotificationForUser(
                    ticket.getUser(),
                    "TICKET_STATUS",
                    "Ticket " + ticket.getTicketNumber() + " Status Changed",
                    "Status updated to: " + newStatus.name() + (request.getNotes() != null ? " (" + request.getNotes() + ")" : ""),
                    ticket.getTrip(),
                    ticket.getBus()
            );
        } catch (Exception e) {
            log.warn("Failed to notify user of status update: {}", e.getMessage());
        }

        return mapToResponse(ticket, true, true, true);
    }

    @Override
    @Transactional
    public SupportTicketResponse updatePriority(Long ticketId, Long adminUserId, UpdateTicketPriorityRequest request) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + ticketId));

        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found with id: " + adminUserId));

        TicketPriority oldPriority = ticket.getPriority();
        TicketPriority newPriority = request.getPriority();

        ticket.setPriority(newPriority);
        ticket = ticketRepository.save(ticket);

        logHistory(ticket, admin, "PRIORITY_CHANGED", oldPriority.name(), newPriority.name(), request.getNotes());

        return mapToResponse(ticket, true, true, true);
    }

    @Override
    @Transactional
    public SupportTicketResponse assignTicket(Long ticketId, Long adminUserId, AssignTicketRequest request) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + ticketId));

        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found with id: " + adminUserId));

        User assignee = userRepository.findById(request.getAssignedToUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found with id: " + request.getAssignedToUserId()));

        String oldAssignee = ticket.getAssignedTo() != null ? ticket.getAssignedTo().getName() : "Unassigned";
        ticket.setAssignedTo(assignee);
        ticket = ticketRepository.save(ticket);

        logHistory(ticket, admin, "ASSIGNED", oldAssignee, assignee.getName(), request.getNotes());

        // Notify newly assigned staff
        try {
            notificationService.createNotificationForUser(
                    assignee,
                    "TICKET_ASSIGNED",
                    "Ticket Assigned: " + ticket.getTicketNumber(),
                    "You have been assigned to handle support ticket " + ticket.getTicketNumber() + ": " + ticket.getSubject(),
                    ticket.getTrip(),
                    ticket.getBus()
            );
        } catch (Exception e) {
            log.warn("Failed to notify assigned staff: {}", e.getMessage());
        }

        return mapToResponse(ticket, true, true, true);
    }

    private void logHistory(SupportTicket ticket, User user, String action, String oldValue, String newValue, String notes) {
        SupportTicketHistory history = SupportTicketHistory.builder()
                .ticket(ticket)
                .changedBy(user)
                .action(action)
                .oldValue(oldValue)
                .newValue(newValue)
                .notes(notes)
                .build();
        historyRepository.save(history);
    }

    private SupportTicketResponse mapToResponse(SupportTicket t, boolean includeAttachments, boolean includeMessages, boolean includeHistory) {
        List<SupportAttachmentResponse> attachments = null;
        if (includeAttachments && t.getAttachments() != null) {
            attachments = t.getAttachments().stream().map(this::mapToAttachmentResponse).collect(Collectors.toList());
        }

        List<SupportMessageResponse> messages = null;
        if (includeMessages && t.getMessages() != null) {
            messages = t.getMessages().stream().map(this::mapToMessageResponse).collect(Collectors.toList());
        }

        List<SupportTicketHistoryResponse> history = null;
        if (includeHistory && t.getHistory() != null) {
            history = t.getHistory().stream().map(this::mapToHistoryResponse).collect(Collectors.toList());
        }

        return SupportTicketResponse.builder()
                .id(t.getId())
                .ticketNumber(t.getTicketNumber())
                .userId(t.getUser() != null ? t.getUser().getId() : null)
                .userName(t.getUser() != null ? t.getUser().getName() : null)
                .userEmail(t.getUser() != null ? t.getUser().getEmail() : null)
                .userRole(t.getUser() != null && t.getUser().getRole() != null ? t.getUser().getRole().name() : null)
                .category(t.getCategory())
                .subject(t.getSubject())
                .description(t.getDescription())
                .status(t.getStatus())
                .priority(t.getPriority())
                .busId(t.getBus() != null ? t.getBus().getId() : null)
                .busNumber(t.getBus() != null ? t.getBus().getBusNumber() : null)
                .tripId(t.getTrip() != null ? t.getTrip().getId() : null)
                .assignedToId(t.getAssignedTo() != null ? t.getAssignedTo().getId() : null)
                .assignedToName(t.getAssignedTo() != null ? t.getAssignedTo().getName() : null)
                .resolvedAt(t.getResolvedAt())
                .closedAt(t.getClosedAt())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .messageCount(t.getMessages() != null ? t.getMessages().size() : 0)
                .attachments(attachments)
                .messages(messages)
                .history(history)
                .build();
    }

    private SupportMessageResponse mapToMessageResponse(SupportMessage m) {
        List<SupportAttachmentResponse> atts = null;
        if (m.getAttachments() != null) {
            atts = m.getAttachments().stream().map(this::mapToAttachmentResponse).collect(Collectors.toList());
        }
        return SupportMessageResponse.builder()
                .id(m.getId())
                .ticketId(m.getTicket() != null ? m.getTicket().getId() : null)
                .senderId(m.getSender() != null ? m.getSender().getId() : null)
                .senderName(m.getSender() != null ? m.getSender().getName() : "Unknown")
                .senderEmail(m.getSender() != null ? m.getSender().getEmail() : null)
                .senderRole(m.getSender() != null && m.getSender().getRole() != null ? m.getSender().getRole().name() : null)
                .message(m.getMessage())
                .isAdminReply(m.getIsAdminReply())
                .attachments(atts)
                .createdAt(m.getCreatedAt())
                .build();
    }

    private SupportAttachmentResponse mapToAttachmentResponse(SupportAttachment a) {
        return SupportAttachmentResponse.builder()
                .id(a.getId())
                .fileName(a.getFileName())
                .fileType(a.getFileType())
                .fileSize(a.getFileSize())
                .fileData(a.getFileData())
                .createdAt(a.getCreatedAt())
                .build();
    }

    private SupportTicketHistoryResponse mapToHistoryResponse(SupportTicketHistory h) {
        return SupportTicketHistoryResponse.builder()
                .id(h.getId())
                .ticketId(h.getTicket() != null ? h.getTicket().getId() : null)
                .changedById(h.getChangedBy() != null ? h.getChangedBy().getId() : null)
                .changedByName(h.getChangedBy() != null ? h.getChangedBy().getName() : "System")
                .changedByRole(h.getChangedBy() != null && h.getChangedBy().getRole() != null ? h.getChangedBy().getRole().name() : null)
                .action(h.getAction())
                .oldValue(h.getOldValue())
                .newValue(h.getNewValue())
                .notes(h.getNotes())
                .createdAt(h.getCreatedAt())
                .build();
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
