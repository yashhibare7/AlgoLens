package com.algolens.dto.credit;

import java.util.Map;

/**
 * @param enforced when false, actions are still metered and logged but never refused. Lets the
 *                 whole credit system be developed and observed before it gates anyone.
 * @param costs    price list, so the UI never hardcodes numbers the backend owns
 */
public record CreditBalanceResponse(int balance, boolean enforced, Map<String, Integer> costs) {
}
