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

import org.jooby.MediaType;
import org.jooby.Result;
import org.jooby.Results;
import org.jooby.Status;
import org.jooby.mvc.GET;
import org.jooby.mvc.Local;
import org.jooby.mvc.POST;
import org.jooby.mvc.Path;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillClock;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.plugin.api.PluginCallContext;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.util.callcontext.CallContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Servlet to complete an Instant Bank Pay flow: fulfil the Billing Request and store the payment ID
 * so Kill Bill can resolve it via getPaymentInfo.
 *
 * GET or POST /plugins/killbill-gocardless/instant/complete?billing_request_id=BRQxxx
 * Optional query: kbAccountId (for context). Tenant is taken from Kill Bill context.
 */
@Singleton
@Path("/instant/complete")
public class GoCardlessInstantCompleteServlet {

    private final OSGIKillbillClock clock;
    private final GoCardlessPaymentPluginApi goCardlessPaymentPluginApi;
    private static final Logger logger = LoggerFactory.getLogger(GoCardlessInstantCompleteServlet.class);

    @javax.inject.Inject
    public GoCardlessInstantCompleteServlet(final OSGIKillbillClock clock,
                                            final GoCardlessPaymentPluginApi goCardlessPaymentPluginApi) {
        this.clock = clock;
        this.goCardlessPaymentPluginApi = goCardlessPaymentPluginApi;
    }

    @GET
    public Result completeGet(@Named("billing_request_id") final String billingRequestId,
                              @Named("kbAccountId") final Optional<UUID> kbAccountId,
                              @Local @Named("killbill_tenant") final Tenant tenant) throws PaymentPluginApiException {
        return doComplete(billingRequestId, kbAccountId.orElse(null), tenant);
    }

    @POST
    public Result completePost(@Named("billing_request_id") final String billingRequestId,
                               @Named("kbAccountId") final Optional<UUID> kbAccountId,
                               @Local @Named("killbill_tenant") final Tenant tenant) throws PaymentPluginApiException {
        return doComplete(billingRequestId, kbAccountId.orElse(null), tenant);
    }

    private Result doComplete(final String billingRequestId, final UUID kbAccountId, final Tenant tenant)
            throws PaymentPluginApiException {
        logger.info("instant/complete: billing_request_id={}", billingRequestId);

        if (billingRequestId == null || billingRequestId.isEmpty()) {
            Map<String, Object> errBody = new HashMap<>();
            errBody.put("error", "Missing billing_request_id");
            return Results.with(errBody, Status.BAD_REQUEST).type(MediaType.json);
        }

        CallContext context = new PluginCallContext(GoCardlessActivator.PLUGIN_NAME, clock.getClock().getUTCNow(),
                kbAccountId != null ? kbAccountId : UUID.randomUUID(), tenant.getId());

        try {
            Map<String, Object> result = goCardlessPaymentPluginApi.fulfilInstantCheckoutAndStore(billingRequestId, context);
            return Results.with(result, Status.OK).type(MediaType.json);
        } catch (PaymentPluginApiException e) {
            logger.warn("Error completing instant checkout", e);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("error", e.getMessage());
            return Results.with(err, Status.BAD_REQUEST).type(MediaType.json);
        }
    }
}
