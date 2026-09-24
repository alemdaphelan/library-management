package com.huit.library.modules.finance.controller;
import com.huit.library.core.response.ApiResponse;
import com.huit.library.modules.finance.dto.PaymentRequestDTO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transactions")
public class PaymentController {
    @PostMapping("/pay")
    public ApiResponse<Boolean> processPenalty(@Valid @RequestBody PaymentRequestDTO req) {
        return ApiResponse.success(true);
    }
}