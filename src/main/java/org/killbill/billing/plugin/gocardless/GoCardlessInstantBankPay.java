/*
 * Copyright 2021 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.killbill.billing.plugin.gocardless;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.plugin.util.KillBillMoney;
import org.killbill.billing.util.callcontext.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gocardless.GoCardlessClient;
import com.gocardless.errors.GoCardlessApiException;
import com.gocardless.resources.BillingRequest;
import com.gocardless.resources.BillingRequestFlow;

/**
 * Helper for GoCardless Instant Bank Pay (IBP) using Billing Requests.
 * IBP allows one-off instant payments without a mandate; funds are confirmed within minutes.
 *
 * @see <a href="https://developer.gocardless.com/billing-requests/taking-an-instant-bank-payment">Taking an Instant Bank Payment</a>
 */
public final class GoCardlessInstantBankPay {

    private static final Logger logger = LoggerFactory.getLogger(GoCardlessInstantBankPay.class);

    private GoCardlessInstantBankPay() {}

    /**
     * Creates a Billing Request and Billing Request Flow for an instant bank payment.
     * The customer should be redirected to the returned URL to authorise the payment.
     *
     * @param client GoCardless client
     * @param amount amount in the currency's major unit (e.g. 10.00 GBP)
     * @param currency currency code (e.g. GBP, EUR)
     * @param kbAccountId Kill Bill account ID (stored in metadata for fulfil step)
     * @param kbPaymentId Kill Bill payment ID (stored in metadata)
     * @param kbTransactionId Kill Bill transaction ID (stored in metadata)
     * @param description human-readable description shown to the payer
     * @param redirectUri URL to redirect to after the customer completes the flow (success or exit)
     * @return result with authorization URL and billing request ID
     */
    public static InstantCheckoutResult createInstantCheckout(
            final GoCardlessClient client,
            final BigDecimal amount,
            final Currency currency,
            final UUID kbAccountId,
            final UUID kbPaymentId,
            final UUID kbTransactionId,
            final String description,
            final String redirectUri) throws GoCardlessApiException {

        int amountMinor = Math.toIntExact(KillBillMoney.toMinorUnits(currency.toString(), amount));
        String currencyCode = currency.toString();

        BillingRequest billingRequest = client.billingRequests().create()
                .withPaymentRequestAmount(amountMinor)
                .withPaymentRequestCurrency(currencyCode)
                .withPaymentRequestDescription(description != null ? description : "Instant payment")
                .withMetadata("kbAccountId", kbAccountId.toString())
                .withMetadata("kbPaymentId", kbPaymentId.toString())
                .withMetadata("kbTransactionId", kbTransactionId.toString())
                .execute();

        logger.info("Created Billing Request for IBP: id={}", billingRequest.getId());

        BillingRequestFlow flow = client.billingRequestFlows().create()
                .withLinksBillingRequest(billingRequest.getId())
                .withRedirectUri(redirectUri)
                .execute();

        // SDK uses British spelling: getAuthorisationUrl()
        String authorizationUrl = flow.getAuthorisationUrl();
        if (authorizationUrl == null || authorizationUrl.isEmpty()) {
            authorizationUrl = flow.getRedirectUri();
        }
        if (authorizationUrl == null || authorizationUrl.isEmpty()) {
            throw new IllegalStateException("Billing Request Flow did not return an authorization/redirect URL");
        }

        return new InstantCheckoutResult(authorizationUrl, billingRequest.getId());
    }

    /**
     * Fulfils a Billing Request that is ready_to_fulfil (after the customer has completed the flow).
     * This creates the payment and returns the GoCardless payment ID.
     *
     * @param client GoCardless client
     * @param billingRequestId the Billing Request ID (e.g. from redirect query param)
     * @return the created payment ID, or null if the billing request has no payment link
     */
    public static String fulfilBillingRequest(final GoCardlessClient client, final String billingRequestId)
            throws GoCardlessApiException {
        BillingRequest billingRequest = client.billingRequests().fulfil(billingRequestId).execute();
        logger.info("Fulfilled Billing Request: id={}", billingRequestId);

        // After fulfil, the payment is linked via payment_request_payment
        if (billingRequest.getLinks() == null) {
            return null;
        }
        return billingRequest.getLinks().getPaymentRequestPayment();
    }

    /**
     * Result of creating an instant checkout session.
     */
    public static final class InstantCheckoutResult {
        private final String authorizationUrl;
        private final String billingRequestId;

        public InstantCheckoutResult(final String authorizationUrl, final String billingRequestId) {
            this.authorizationUrl = authorizationUrl;
            this.billingRequestId = billingRequestId;
        }

        public String getAuthorizationUrl() {
            return authorizationUrl;
        }

        public String getBillingRequestId() {
            return billingRequestId;
        }

        public Map<String, String> toMap() {
            Map<String, String> m = new HashMap<>();
            m.put("formUrl", authorizationUrl);
            m.put("billingRequestId", billingRequestId);
            return m;
        }
    }
}
