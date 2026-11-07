package com.smartbus.dto.request;

import com.smartbus.entity.TicketPriority;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTicketPriorityRequest {

    @NotNull(message = "Priority is required")
    private TicketPriority priority;

    private String notes;
}
