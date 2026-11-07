package com.smartbus.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicketHistoryResponse {
    private Long id;
    private Long ticketId;
    private Long changedById;
    private String changedByName;
    private String changedByRole;
    private String action;
    private String oldValue;
    private String newValue;
    private String notes;
    private LocalDateTime createdAt;
}
