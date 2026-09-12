package com.algolens.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.algolens.config.AppProperties;
import com.algolens.entity.CreditTransaction;
import com.algolens.entity.CreditTransactionType;
import com.algolens.entity.Role;
import com.algolens.entity.User;
import com.algolens.exception.InsufficientCreditsException;
import com.algolens.repository.CreditTransactionRepository;
import com.algolens.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guards the two invariants the whole billing story rests on: the ledger always explains the
 * balance, and enforcement can be switched on without touching any calling code.
 */
@SpringBootTest
@ActiveProfiles("test")
class CreditServiceTest {

    @Autowired
    private CreditService credits;

    @Autowired
    private UserRepository users;

    @Autowired
    private CreditTransactionRepository transactions;

    @Autowired
    private AppProperties properties;

    private Long userId;

    @BeforeEach
    void createUser() {
        User user = users.save(new User("Ledger Tester",
                "ledger-" + java.util.UUID.randomUUID() + "@example.com", "hash", Role.USER, 0));
        credits.grantSignupBonus(user);
        userId = user.getId();
    }

    @Test
    @DisplayName("the signup grant is recorded as a transaction, not just a balance")
    void signupGrantIsLedgered() {
        assertThat(credits.getBalance(userId)).isEqualTo(100);

        List<CreditTransaction> ledger = ledger();
        assertThat(ledger).hasSize(1);
        assertThat(ledger.get(0).getType()).isEqualTo(CreditTransactionType.SIGNUP_GRANT);
        assertThat(ledger.get(0).getAmount()).isEqualTo(100);
        assertThat(ledger.get(0).getBalanceAfter()).isEqualTo(100);
    }

    @Test
    @DisplayName("the ledger always sums to the cached balance")
    void ledgerReconcilesWithTheBalance() {
        credits.consume(userId, 5, CreditTransactionType.AI_EXPLANATION, "explain");
        credits.consume(userId, 1, CreditTransactionType.CODE_EXECUTION, "run");
        credits.add(userId, 20, CreditTransactionType.PURCHASE, "top up");

        int fromLedger = ledger().stream().mapToInt(CreditTransaction::getAmount).sum();
        assertThat(fromLedger).isEqualTo(credits.getBalance(userId)).isEqualTo(114);
    }

    @Test
    @DisplayName("with enforcement off, an overdraft is recorded rather than refused")
    void meteringModeDoesNotRefuse() {
        assertThat(properties.credits().enforced()).isFalse();

        int balance = credits.consume(userId, 500, CreditTransactionType.AI_ANALYSIS, "big ask");

        assertThat(balance).isEqualTo(-400);
        assertThat(ledger()).anyMatch(t -> t.getAmount() == -500);
    }

    @Test
    @DisplayName("a zero-cost action writes nothing to the ledger")
    void freeActionsAreNotLedgered() {
        credits.consume(userId, 0, CreditTransactionType.CODE_EXECUTION, "free");
        assertThat(ledger()).hasSize(1); // the signup grant only
    }

    @Test
    @DisplayName("credit additions must be positive")
    void additionsMustBePositive() {
        assertThatThrownBy(() -> credits.add(userId, -5, CreditTransactionType.PURCHASE, "bad"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the monthly allowance is granted once when the last grant was a month ago")
    @Transactional
    void monthlyGrantTopsUpOncePerMonth() {
        User user = users.findById(userId).orElseThrow();
        user.setLastMonthlyGrantAt(Instant.now().minus(40, ChronoUnit.DAYS));
        users.saveAndFlush(user);

        int first = credits.getBalance(userId);
        int second = credits.getBalance(userId);

        assertThat(first).isEqualTo(200);
        assertThat(second).as("a second read in the same month must not grant again")
                .isEqualTo(200);
        assertThat(ledger()).filteredOn(t -> t.getType() == CreditTransactionType.MONTHLY_GRANT)
                .hasSize(1);
    }

    @Test
    @DisplayName("InsufficientCreditsException reports what was needed and what was available")
    void insufficientCreditsCarriesTheNumbers() {
        InsufficientCreditsException exception = new InsufficientCreditsException(5, 2);
        assertThat(exception.required()).isEqualTo(5);
        assertThat(exception.available()).isEqualTo(2);
        assertThat(exception.getMessage()).contains("5").contains("2");
    }

    private List<CreditTransaction> ledger() {
        return transactions.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 50))
                .getContent();
    }
}
