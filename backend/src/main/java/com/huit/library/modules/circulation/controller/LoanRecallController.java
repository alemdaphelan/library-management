package com.huit.library.modules.circulation.controller;

import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.circulation.dto.LoanItemDTO;
import com.huit.library.modules.circulation.dto.LoanRecallModels.*;
import com.huit.library.modules.circulation.service.LoanRecallService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/loans")
@PreAuthorize("hasAnyRole('ADMIN','LIBRARIAN')")
public class LoanRecallController {
    private final LoanRecallService recalls;
    public LoanRecallController(LoanRecallService recalls) { this.recalls=recalls; }
    @GetMapping("/recall-items")
    public Page list(@RequestParam(defaultValue="") String q, @RequestParam(defaultValue="ALL") Filter state,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size) {
        return recalls.list(q,state,page,size);
    }
    @PostMapping("/{ticketId}/items/{detailId}/recall")
    public LoanItemDTO request(@PathVariable Long ticketId,@PathVariable Long detailId,@Valid @RequestBody Request request,
            @AuthenticationPrincipal CustomUserDetails staff) {
        return recalls.request(ticketId,detailId,request.reason(),staff.getUser().getUserId());
    }
}
