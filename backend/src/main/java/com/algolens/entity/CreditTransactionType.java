package com.algolens.entity;

/**
 * Why a balance changed. Every row in {@code credit_transactions} carries one of these,
 * which is what makes the ledger auditable after the fact.
 */
public enum CreditTransactionType {

    SIGNUP_GRANT(true),
    MONTHLY_GRANT(true),
    PURCHASE(true),
    REFUND(true),
    ADMIN_ADJUSTMENT(true),

    CODE_EXECUTION(false),
    CODE_SUBMISSION(false),
    AI_EXPLANATION(false),
    AI_ANALYSIS(false);

    private final boolean credit;

    CreditTransactionType(boolean credit) {
        this.credit = credit;
    }

    /** True when this type adds credits, false when it consumes them. */
    public boolean isCredit() {
        return credit;
    }
}
