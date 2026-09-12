package com.algolens.dto.credit;

import com.algolens.entity.CreditTransaction;
import java.time.Instant;

public record CreditTransactionResponse(
        Long id,
        int amount,
        String type,
        String description,
        int balanceAfter,
        Instant createdAt) {

    public static CreditTransactionResponse from(CreditTransaction transaction) {
        return new CreditTransactionResponse(
                transaction.getId(),
                transaction.getAmount(),
                transaction.getType().name(),
                transaction.getDescription(),
                transaction.getBalanceAfter(),
                transaction.getCreatedAt());
    }
}
