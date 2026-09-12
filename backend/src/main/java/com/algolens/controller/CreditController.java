package com.algolens.controller;

import com.algolens.dto.PageResponse;
import com.algolens.dto.credit.CreditBalanceResponse;
import com.algolens.dto.credit.CreditTransactionResponse;
import com.algolens.security.AuthUser;
import com.algolens.service.CreditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.function.Function;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/credits")
@Tag(name = "Credits")
public class CreditController {

    private final CreditService creditService;

    public CreditController(CreditService creditService) {
        this.creditService = creditService;
    }

    @GetMapping
    @Operation(summary = "My balance, the price list, and whether credits are enforced")
    public CreditBalanceResponse balance(@AuthenticationPrincipal AuthUser user) {
        return creditService.getBalanceDetail(user.id());
    }

    @GetMapping("/transactions")
    @Operation(summary = "My credit ledger, newest first")
    public PageResponse<CreditTransactionResponse> transactions(
            @AuthenticationPrincipal AuthUser user,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Pageable pageable = Pagination.of(page, size);
        return PageResponse.of(creditService.history(user.id(), pageable), Function.identity());
    }
}
