package com.smartbus.dto.response;

import com.smartbus.entity.TicketCategory;
import com.smartbus.entity.TicketPriority;
import com.smartbus.entity.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicketResponse {
    private Long id;
    private String ticketNumber;
    private Long userId;
    private String userName;
    private String userEmail;
    private String userRole;
    private TicketCategory category;
    private String subject;
    private String description;
    private TicketStatus status;
    private TicketPriority priority;
    private Long busId;
    private String busNumber;
    private Long tripId;
    private Long assignedToId;
    private String assignedToName;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer messageCount;
    private List<SupportAttachmentResponse> attachments;
    private List<SupportMessageResponse> messages;
    private List<SupportTicketHistoryResponse> history;
}
