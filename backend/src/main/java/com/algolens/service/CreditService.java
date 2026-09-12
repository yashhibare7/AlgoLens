package com.algolens.service;

import com.algolens.config.AppProperties;
import com.algolens.dto.credit.CreditBalanceResponse;
import com.algolens.dto.credit.CreditTransactionResponse;
import com.algolens.entity.CreditTransaction;
import com.algolens.entity.CreditTransactionType;
import com.algolens.entity.User;
import com.algolens.exception.InsufficientCreditsException;
import com.algolens.exception.NotFoundException;
import com.algolens.repository.CreditTransactionRepository;
import com.algolens.repository.UserRepository;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The credit ledger.
 *
 * <p>Two invariants hold everywhere:
 *
 * <ol>
 *   <li><b>Nothing changes a balance without writing a transaction.</b> Every mutation goes
 *       through {@link #apply}, which updates {@code users.credit_balance} and appends a
 *       {@link CreditTransaction} in the same database transaction. The ledger can therefore
 *       always be replayed to reconcile the cached balance -- which is what makes billing
 *       disputes answerable instead of guesswork.</li>
 *   <li><b>Metering and enforcement are separate.</b> With
 *       {@code algolens.credits.enforced=false} consumption is still recorded, it just never
 *       refuses a request. So the whole system can be built, observed and its prices tuned
 *       against real usage long before it gates a single user.</li>
 * </ol>
 */
@Service
public class CreditService {

    private static final Logger log = LoggerFactory.getLogger(CreditService.class);

    private final UserRepository users;
    private final CreditTransactionRepository transactions;
    private final AppProperties.Credits config;

    public CreditService(UserRepository users, CreditTransactionRepository transactions,
            AppProperties properties) {
        this.users = users;
        this.transactions = transactions;
        this.config = properties.credits();
    }

    public AppProperties.Credits config() {
        return config;
    }

    // ------------------------------------------------------------------ reads

    @Transactional
    public int getBalance(Long userId) {
        User user = lockedUser(userId);
        grantMonthlyIfDue(user);
        return user.getCreditBalance();
    }

    @Transactional
    public CreditBalanceResponse getBalanceDetail(Long userId) {
        return new CreditBalanceResponse(getBalance(userId), config.enforced(), priceList());
    }

    @Transactional(readOnly = true)
    public Page<CreditTransactionResponse> history(Long userId, Pageable pageable) {
        return transactions.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(CreditTransactionResponse::from);
    }

    public Map<String, Integer> priceList() {
        Map<String, Integer> costs = new LinkedHashMap<>();
        costs.put("CODE_EXECUTION", config.costExecution());
        costs.put("AI_EXPLANATION", config.costAiExplanation());
        costs.put("AI_ANALYSIS", config.costAiAnalysis());
        return costs;
    }

    // ------------------------------------------------------------------ mutations

    /** Grants the one-time signup allowance. Called from registration, inside its transaction. */
    @Transactional
    public void grantSignupBonus(User user) {
        user.setLastMonthlyGrantAt(Instant.now());
        apply(user, config.signupGrant(), CreditTransactionType.SIGNUP_GRANT,
                "Welcome bonus: " + config.signupGrant() + " credits");
    }

    /**
     * Spends credits.
     *
     * @return the balance after the charge
     * @throws InsufficientCreditsException when enforcement is on and the balance is too low
     */
    @Transactional
    public int consume(Long userId, int amount, CreditTransactionType type, String description) {
        if (amount <= 0) {
            return getBalance(userId);
        }
        User user = lockedUser(userId);
        grantMonthlyIfDue(user);

        if (user.getCreditBalance() < amount) {
            if (config.enforced()) {
                throw new InsufficientCreditsException(amount, user.getCreditBalance());
            }
            // Metering mode: record the overdraft rather than hiding it, so the numbers used to
            // set prices later reflect what people actually did.
            log.info("User {} went {} credit(s) past their balance on {} (enforcement off)",
                    userId, amount - user.getCreditBalance(), type);
        }
        return apply(user, -amount, type, description);
    }

    @Transactional
    public int add(Long userId, int amount, CreditTransactionType type, String description) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Credit additions must be positive");
        }
        return apply(lockedUser(userId), amount, type, description);
    }

    // ------------------------------------------------------------------ internals

    /** The only place a balance is allowed to move. */
    private int apply(User user, int signedAmount, CreditTransactionType type,
            String description) {
        int updated = user.getCreditBalance() + signedAmount;
        user.setCreditBalance(updated);
        users.save(user);
        transactions.save(new CreditTransaction(user, signedAmount, type, description, updated));
        return updated;
    }

    /**
     * Tops the account up once per calendar month, lazily on first use rather than from a
     * scheduled job. One less moving part, and it cannot double-grant: the guard is the stored
     * timestamp, so a restart or a second concurrent request changes nothing.
     */
    private void grantMonthlyIfDue(User user) {
        Instant last = user.getLastMonthlyGrantAt();
        YearMonth now = YearMonth.from(Instant.now().atZone(ZoneOffset.UTC));
        if (last != null && YearMonth.from(last.atZone(ZoneOffset.UTC)).equals(now)) {
            return;
        }
        user.setLastMonthlyGrantAt(Instant.now());
        apply(user, config.monthlyGrant(), CreditTransactionType.MONTHLY_GRANT,
                "Monthly allowance for " + now);
        log.info("Granted {} monthly credits to user {}", config.monthlyGrant(), user.getId());
    }

    private User lockedUser(Long userId) {
        return users.findByIdForUpdate(userId)
                .orElseThrow(() -> NotFoundException.of("User", userId));
    }
}
