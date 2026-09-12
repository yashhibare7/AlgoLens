package com.algolens.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Append-only credit ledger entry. {@code amount} is signed (+100 grant, -1 execution) and
 * {@code balanceAfter} snapshots the resulting balance so the ledger can be reconciled
 * against {@code users.credit_balance} at any time.
 */
@Entity
@Table(name = "credit_transactions")
@EntityListeners(AuditingEntityListener.class)
public class CreditTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private CreditTransactionType type;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CreditTransaction() {
        // for JPA
    }

    public CreditTransaction(User user, int amount, CreditTransactionType type, String description,
            int balanceAfter) {
        this.user = user;
        this.amount = amount;
        this.type = type;
        this.description = description;
        this.balanceAfter = balanceAfter;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public int getAmount() {
        return amount;
    }

    public CreditTransactionType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public int getBalanceAfter() {
        return balanceAfter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
