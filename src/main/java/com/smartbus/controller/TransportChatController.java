package com.smartbus.controller;

import com.smartbus.dto.request.TransportChatMessageRequest;
import com.smartbus.dto.response.ApiResponse;
import com.smartbus.dto.response.TransportChatInitResponse;
import com.smartbus.dto.response.TransportChatMessageResponse;
import com.smartbus.entity.User;
import com.smartbus.exception.BadRequestException;
import com.smartbus.exception.ResourceNotFoundException;
import com.smartbus.repository.UserRepository;
import com.smartbus.service.impl.TransportChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transport-chat")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Smart Transport Assistant", description = "AI-powered transport chatbot endpoints")
@SecurityRequirement(name = "bearerAuth")
public class TransportChatController {

    private final TransportChatService chatService;
    private final UserRepository userRepository;

    @GetMapping("/init")
    @Operation(summary = "Get dynamic role-specific runtime context and suggested questions")
    public ResponseEntity<ApiResponse<TransportChatInitResponse>> getInitContext(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);
        TransportChatInitResponse init = chatService.getInitContext(user);
        return ResponseEntity.ok(ApiResponse.success(init));
    }

    @PostMapping("/message")
    @Operation(summary = "Send a question to Smart Transport Assistant")
    public ResponseEntity<ApiResponse<TransportChatMessageResponse>> sendMessage(
            @Valid @RequestBody TransportChatMessageRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);
        TransportChatMessageResponse response = chatService.processMessage(user, request.getMessage());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/history")
    @Operation(summary = "Get user's transport chat history")
    public ResponseEntity<ApiResponse<List<TransportChatMessageResponse>>> getHistory(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);
        List<TransportChatMessageResponse> history = chatService.getHistory(user);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @DeleteMapping("/history")
    @Operation(summary = "Clear user's transport chat history")
    public ResponseEntity<ApiResponse<Void>> clearHistory(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);
        chatService.clearHistory(user);
        return ResponseEntity.ok(ApiResponse.success("Chat history cleared successfully", null));
    }

    private User getAuthenticatedUser(UserDetails userDetails) {
        if (userDetails == null || userDetails.getUsername() == null) {
            throw new BadRequestException("Authentication required to interact with Smart Transport Assistant");
        }
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userDetails.getUsername()));
    }
}
