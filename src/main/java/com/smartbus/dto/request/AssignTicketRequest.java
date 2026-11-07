package com.smartbus.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignTicketRequest {

    @NotNull(message = "Assigned user ID is required")
    private Long assignedToUserId;

    private String notes;
}
