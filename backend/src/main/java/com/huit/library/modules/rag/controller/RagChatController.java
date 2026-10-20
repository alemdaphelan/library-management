package com.huit.library.modules.rag.controller;

import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.rag.dto.RagChatRequestDTO;
import com.huit.library.modules.rag.dto.RagChatResponseDTO;
import com.huit.library.modules.rag.service.RagChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chatbot")
@Tag(name = "RAG Chatbot", description = "Grounded library assistant with source citations")
public class RagChatController {
    private final RagChatService chatService;

    public RagChatController(RagChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/query")
    @Operation(summary = "Ask the HUIT Library RAG assistant")
    public RagChatResponseDTO query(
            @Valid @RequestBody RagChatRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest httpRequest) {
        String actorKey = principal == null
                ? "anonymous:" + httpRequest.getRemoteAddr()
                : "user:" + principal.getUser().getUserId();
        return chatService.ask(request.message(), actorKey);
    }
}
