package com.smartbus.service;

import com.smartbus.dto.request.*;
import com.smartbus.dto.response.SupportMessageResponse;
import com.smartbus.dto.response.SupportTicketResponse;
import com.smartbus.entity.TicketCategory;
import com.smartbus.entity.TicketPriority;
import com.smartbus.entity.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SupportTicketService {

    SupportTicketResponse createTicket(Long userId, CreateTicketRequest request);

    Page<SupportTicketResponse> getMyTickets(Long userId, Pageable pageable);

    List<SupportTicketResponse> getMyTicketsList(Long userId);

    SupportTicketResponse getTicketDetails(Long ticketId, Long currentUserId, boolean isAdmin);

    SupportMessageResponse addMessage(Long ticketId, Long currentUserId, CreateTicketMessageRequest request, boolean isAdmin);

    List<SupportMessageResponse> getMessages(Long ticketId, Long currentUserId, boolean isAdmin);

    Page<SupportTicketResponse> getAllTicketsAdmin(
            TicketStatus status,
            TicketPriority priority,
            TicketCategory category,
            String search,
            Pageable pageable
    );

    SupportTicketResponse updateStatus(Long ticketId, Long adminUserId, UpdateTicketStatusRequest request);

    SupportTicketResponse updatePriority(Long ticketId, Long adminUserId, UpdateTicketPriorityRequest request);

    SupportTicketResponse assignTicket(Long ticketId, Long adminUserId, AssignTicketRequest request);
}
