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

import org.jooby.MediaType;
import org.jooby.Result;
import org.jooby.Results;
import org.jooby.Status;
import org.jooby.mvc.Local;
import org.jooby.mvc.POST;
import org.jooby.mvc.Path;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillClock;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.plugin.api.PluginCallContext;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.util.callcontext.CallContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gocardless.errors.GoCardlessApiException;

import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Servlet for Instant Bank Pay (IBP) checkout.
 * Creates a Billing Request and Billing Request Flow; the client redirects the customer to the returned URL.
 *
 * POST /plugins/killbill-gocardless/checkout/instant
 * Query/body: kbAccountId, amount, currency, kbPaymentId, kbTransactionId, success_redirect_url, description (optional)
 */
@Singleton
@Path("/checkout/instant")
public class GoCardlessInstantCheckoutServlet {

    private final OSGIKillbillClock clock;
    private final GoCardlessPaymentPluginApi goCardlessPaymentPluginApi;
    private static final Logger logger = LoggerFactory.getLogger(GoCardlessInstantCheckoutServlet.class);

    @javax.inject.Inject
    public GoCardlessInstantCheckoutServlet(final OSGIKillbillClock clock,
                                           final GoCardlessPaymentPluginApi goCardlessPaymentPluginApi) {
        this.clock = clock;
        this.goCardlessPaymentPluginApi = goCardlessPaymentPluginApi;
    }

    @POST
    public Result createInstantCheckout(
            @Named("kbAccountId") final UUID kbAccountId,
            @Named("amount") final BigDecimal amount,
            @Named("currency") final String currencyCode,
            @Named("kbPaymentId") final UUID kbPaymentId,
            @Named("kbTransactionId") final UUID kbTransactionId,
            @Named("success_redirect_url") final String successRedirectUrl,
            @Named("description") final java.util.Optional<String> description,
            @Local @Named("killbill_tenant") final Tenant tenant) throws PaymentPluginApiException {
        logger.info("createInstantCheckout: kbAccountId={}, amount={}, currency={}", kbAccountId, amount, currencyCode);

        if (kbAccountId == null || amount == null || currencyCode == null || kbPaymentId == null
                || kbTransactionId == null || successRedirectUrl == null || successRedirectUrl.isEmpty()) {
            Map<String, Object> errBody = new HashMap<>();
            errBody.put("error", "Missing required parameters: kbAccountId, amount, currency, kbPaymentId, kbTransactionId, success_redirect_url");
            return Results.with(errBody, Status.BAD_REQUEST).type(MediaType.json);

        Currency currency;
        try {
            currency = Currency.valueOf(currencyCode);
        } catch (IllegalArgumentException e) {
Map<String, Object> errBody = new HashMap<>();
errBody.put("error", "Invalid currency: " + currencyCode);
return Results.with(errBody, Status.BAD_REQUEST).type(MediaType.json);
        }

        CallContext context = new PluginCallContext(GoCardlessActivator.PLUGIN_NAME, clock.getClock().getUTCNow(), kbAccountId, tenant.getId());

        try {
            GoCardlessInstantBankPay.InstantCheckoutResult result = goCardlessPaymentPluginApi.createInstantCheckoutSession(
                    kbAccountId, amount, currency, kbPaymentId, kbTransactionId,
                    successRedirectUrl, description.orElse("Instant payment"), context);

            Map<String, Object> body = new HashMap<>();
            body.put("formUrl", result.getAuthorizationUrl());
            body.put("formMethod", "GET");
            body.put("billingRequestId", result.getBillingRequestId());
            body.put("kbAccountId", kbAccountId.toString());
            body.put("kbPaymentId", kbPaymentId.toString());
            body.put("kbTransactionId", kbTransactionId.toString());

            return Results.with(body, Status.CREATED).type(MediaType.json);
        } catch (GoCardlessApiException e) {
            logger.warn("GoCardless API error creating instant checkout", e);
            throw new PaymentPluginApiException("GoCardless error: " + e.getMessage(), e);
        }
    }
}
