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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.jooq.UpdatableRecord;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.osgi.libs.killbill.OSGIConfigPropertiesService;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.payment.api.PaymentMethodPlugin;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.plugin.api.GatewayNotification;
import org.killbill.billing.payment.plugin.api.HostedPaymentPageFormDescriptor;
import org.killbill.billing.payment.plugin.api.PaymentMethodInfoPlugin;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.plugin.api.PluginProperties;
import org.killbill.billing.plugin.api.payment.PluginHostedPaymentPageFormDescriptor;
import org.killbill.billing.plugin.api.payment.PluginPaymentPluginApi;
import org.killbill.billing.plugin.api.payment.PluginPaymentTransactionInfoPlugin;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gocardless.GoCardlessClient;
import com.gocardless.errors.GoCardlessApiException;
import com.gocardless.resources.Payment;
import com.gocardless.resources.RedirectFlow;
import com.gocardless.services.RedirectFlowService.RedirectFlowCreateRequest.PrefilledCustomer;
import com.google.common.collect.ImmutableList;
import org.killbill.billing.plugin.api.core.PluginCustomField;
import org.killbill.billing.ObjectType;

public class GoCardlessPaymentPluginApi implements PaymentPluginApi {

	private static final Logger logger = LoggerFactory.getLogger(GoCardlessPaymentPluginApi.class);
	private OSGIKillbillAPI killbillAPI;
	private Clock clock;
	private static String GC_ACCESS_TOKEN_PROPERTY = "GC_ACCESS_TOKEN";

	
	private GoCardlessClient client;

	public GoCardlessPaymentPluginApi(final OSGIKillbillAPI killbillAPI,final Clock clock) { 
		this.killbillAPI = killbillAPI;
		this.clock = clock;
		client = GoCardlessClient.newBuilder(System.getenv(GC_ACCESS_TOKEN_PROPERTY)).withEnvironment(GoCardlessClient.Environment.SANDBOX).build();//"sandbox_FXxlnWbKEleVIIxZ9kS218BgtQBJMscKKNB5b8-S";
	}


	@Override
	public PaymentTransactionInfoPlugin authorizePayment(UUID kbAccountId, UUID kbPaymentId, UUID kbTransactionId,
			UUID kbPaymentMethodId, BigDecimal amount, Currency currency, Iterable<PluginProperty> properties,
			CallContext context) throws PaymentPluginApiException {
		
		return null;
	}

	@Override
	public PaymentTransactionInfoPlugin capturePayment(UUID kbAccountId, UUID kbPaymentId, UUID kbTransactionId,
			UUID kbPaymentMethodId, BigDecimal amount, Currency currency, Iterable<PluginProperty> properties,
			CallContext context) throws PaymentPluginApiException {
		return null;
	}

	@Override
	public PaymentTransactionInfoPlugin purchasePayment(UUID kbAccountId, UUID kbPaymentId, UUID kbTransactionId,
			UUID kbPaymentMethodId, BigDecimal amount, Currency currency, Iterable<PluginProperty> properties,
			CallContext context) throws PaymentPluginApiException {

		logger.info("purchasePayment, kbAccountId=" + kbAccountId);
		
		PaymentTransactionInfoPlugin paymentTransactionInfoPlugin; 
		String mandate = getMandateId(kbAccountId, context); // "MD000E3P8D8SNS";
		logger.info("MandateId:", mandate);
		if (mandate != null) {
			logger.info("Processing payment");
			try {
				String idempotencyKey = PluginProperties.findPluginPropertyValue("idempotencykey", properties); // "random_payment_specific_string";

				com.gocardless.services.PaymentService.PaymentCreateRequest.Currency goCardlessCurrency = convertKillBillCurrencyToGoCardlessCurrency(
						currency);

				Payment payment = client.payments().create().withAmount(amount.intValue())
						.withCurrency(goCardlessCurrency).withLinksMandate(mandate).withIdempotencyKey(idempotencyKey)
						.execute();
				
				List<PluginProperty> outputProperties = new ArrayList<PluginProperty>();
				outputProperties.add(new PluginProperty("paymentId", payment.getId(), false));
				
				paymentTransactionInfoPlugin = new GoCardlessPaymentTransactionInfoPlugin(
						kbPaymentId, kbTransactionId, TransactionType.PURCHASE, amount, currency, PaymentPluginStatus.PROCESSED, null,
						null, String.valueOf(payment.getId()), null, new DateTime(), new DateTime(payment.getCreatedAt()), outputProperties);
				
				logger.info("Payment processed, PaymentId:", payment.getId());
				

			} catch (GoCardlessApiException e) {

				paymentTransactionInfoPlugin = new GoCardlessPaymentTransactionInfoPlugin(
						kbPaymentId, kbTransactionId, TransactionType.PURCHASE, amount, currency,  PaymentPluginStatus.ERROR, e.getErrorMessage(),
						String.valueOf(e.getCode()), null, null, new DateTime(), null, null);
				
				logger.warn("Error occured in purchasePayment", e.getType(), e);
			}
		}
		else {
			logger.warn("Unable to fetch mandate, so cannot process payment");
			paymentTransactionInfoPlugin = new GoCardlessPaymentTransactionInfoPlugin(
					kbPaymentId, kbTransactionId, TransactionType.PURCHASE, amount, currency, PaymentPluginStatus.CANCELED, null, //TODO: Is Canceled status correct here?
					null, null, null, new DateTime(), null, null);
		}

		return paymentTransactionInfoPlugin;
	}

	private String getMandateId(UUID kbAccountId, CallContext context) {
		final List<CustomField> customFields = killbillAPI.getCustomFieldUserApi()
				.getCustomFieldsForAccountType(kbAccountId, ObjectType.ACCOUNT, context);
		String mandateId = null;
		for (final CustomField customField : customFields) {
			if (customField.getFieldName().equals("GOCARDLESS_MANDATE_ID")) {
				mandateId = customField.getFieldValue();
				break;
			}
		}
		return mandateId;

	}

	/**
	 * TODO: See if there is a better way to do this
	 * 
	 * @param currency
	 * @return
	 */
	private com.gocardless.services.PaymentService.PaymentCreateRequest.Currency convertKillBillCurrencyToGoCardlessCurrency(
			Currency currency) {

		switch (currency) {
		case USD:
			return com.gocardless.services.PaymentService.PaymentCreateRequest.Currency.USD;
		case AUD:
			return com.gocardless.services.PaymentService.PaymentCreateRequest.Currency.AUD;
		case CAD:
			return com.gocardless.services.PaymentService.PaymentCreateRequest.Currency.CAD;
		case DKK:
			return com.gocardless.services.PaymentService.PaymentCreateRequest.Currency.DKK;
		case EUR:
			return com.gocardless.services.PaymentService.PaymentCreateRequest.Currency.EUR;
		case GBP:
			return com.gocardless.services.PaymentService.PaymentCreateRequest.Currency.GBP;
		case NZD:
			return com.gocardless.services.PaymentService.PaymentCreateRequest.Currency.NZD;
		case SEK:
			return com.gocardless.services.PaymentService.PaymentCreateRequest.Currency.SEK;
		default:
			return null;

		}
	}

	@Override
	public PaymentTransactionInfoPlugin voidPayment(UUID kbAccountId, UUID kbPaymentId, UUID kbTransactionId,
			UUID kbPaymentMethodId, Iterable<PluginProperty> properties, CallContext context)
			throws PaymentPluginApiException {
		return null;
	}

	@Override
	public PaymentTransactionInfoPlugin creditPayment(UUID kbAccountId, UUID kbPaymentId, UUID kbTransactionId,
			UUID kbPaymentMethodId, BigDecimal amount, Currency currency, Iterable<PluginProperty> properties,
			CallContext context) throws PaymentPluginApiException {
		return null;
	}

	@Override
	public PaymentTransactionInfoPlugin refundPayment(UUID kbAccountId, UUID kbPaymentId, UUID kbTransactionId,
			UUID kbPaymentMethodId, BigDecimal amount, Currency currency, Iterable<PluginProperty> properties,
			CallContext context) throws PaymentPluginApiException {
		return null;
	}

	public List<PaymentTransactionInfoPlugin> getPaymentInfo(UUID kbAccountId, UUID kbPaymentId,
			Iterable<PluginProperty> properties, TenantContext context) throws PaymentPluginApiException {
		return null;
	}

	@Override
	public Pagination<PaymentTransactionInfoPlugin> searchPayments(String searchKey, Long offset, Long limit,
			Iterable<PluginProperty> properties, TenantContext context) throws PaymentPluginApiException {
		return null;
	}

	@Override
	public void addPaymentMethod(UUID kbAccountId, UUID kbPaymentMethodId, PaymentMethodPlugin paymentMethodProps,
			boolean setDefault, Iterable<PluginProperty> properties, CallContext context)
			throws PaymentPluginApiException {
		logger.info("addPaymentMethod, kbAccountId=" + kbAccountId);
		final Iterable<PluginProperty> allProperties = PluginProperties.merge(paymentMethodProps.getProperties(),
				properties);
		String redirectFlowId = PluginProperties.findPluginPropertyValue("redirect_flow_id", allProperties); // "RE000341840PVZQ3X0EVB82BR54QZAQN"
		String sessionToken = PluginProperties.findPluginPropertyValue("session_token", allProperties); // "dummy_session_token"

		try {
			RedirectFlow redirectFlow = client.redirectFlows().complete(redirectFlowId).withSessionToken(sessionToken).execute();

			String mandateId = redirectFlow.getLinks().getMandate();
			logger.info("MandateId:", mandateId);

			try {
				killbillAPI.getCustomFieldUserApi().addCustomFields(ImmutableList.of(new PluginCustomField(kbAccountId,
						ObjectType.ACCOUNT, "GOCARDLESS_MANDATE_ID", mandateId, clock.getUTCNow())), context);
			} catch (CustomFieldApiException e) {
				logger.warn("Error occured while saving mandate id", e);
				throw new PaymentPluginApiException("Error occured while saving mandate id", e);
			}

			// Display a confirmation page to your customer, telling them their Direct Debit
			// has been set up. You could build your own, or use ours, which shows all the
			// relevant information and is translated into all the languages we support.
			System.out.println(redirectFlow.getConfirmationUrl()); // TODO: How to send this to the client application?

		} catch (GoCardlessApiException e) {
			logger.warn("Error occured while completing the GoCardless flow", e.getType(), e);
			throw new PaymentPluginApiException("Error occured while completing the GoCardless flow", e);

		}

	}

	@Override
	public void deletePaymentMethod(UUID kbAccountId, UUID kbPaymentMethodId, Iterable<PluginProperty> properties,
			CallContext context) throws PaymentPluginApiException {
	}

	@Override
	public PaymentMethodPlugin getPaymentMethodDetail(UUID kbAccountId, UUID kbPaymentMethodId,
			Iterable<PluginProperty> properties, TenantContext context) throws PaymentPluginApiException {
		return null;
	}

	@Override
	public void setDefaultPaymentMethod(UUID kbAccountId, UUID kbPaymentMethodId, Iterable<PluginProperty> properties,
			CallContext context) throws PaymentPluginApiException {
	}

	@Override
	public List<PaymentMethodInfoPlugin> getPaymentMethods(UUID kbAccountId, boolean refreshFromGateway,
			Iterable<PluginProperty> properties, CallContext context) throws PaymentPluginApiException {
		return null;
	}

	@Override
	public Pagination<PaymentMethodPlugin> searchPaymentMethods(String searchKey, Long offset, Long limit,
			Iterable<PluginProperty> properties, TenantContext context) throws PaymentPluginApiException {
		return null;
	}

	@Override
	public void resetPaymentMethods(UUID kbAccountId, List<PaymentMethodInfoPlugin> paymentMethods,
			Iterable<PluginProperty> properties, CallContext context) throws PaymentPluginApiException {
	}

	@Override
	public HostedPaymentPageFormDescriptor buildFormDescriptor(UUID kbAccountId, Iterable<PluginProperty> customFields,
			Iterable<PluginProperty> properties, CallContext context) throws PaymentPluginApiException {
		logger.info("buildFormDescriptor, kbAccountId=" + kbAccountId);

		// retrieve properties
		String successRedirectUrl = PluginProperties.findPluginPropertyValue("success_redirect_url", properties); // "https://developer.gocardless.com/example-redirect-uri/"; - this is the URL to which GoCardless will redirect after users set up the  mandate
		String redirectFlowDescription = PluginProperties.findPluginPropertyValue("redirect_flow_description",properties); 
		String sessionToken = PluginProperties.findPluginPropertyValue("session_token", properties); // "dummy_session_token"

		PrefilledCustomer customer = buildCustomer(customFields);// build a PrefilledCuctomer object from custom fields if present

		RedirectFlow redirectFlow = client.redirectFlows().create().withDescription(redirectFlowDescription)
				.withSessionToken(sessionToken) 
				.withSuccessRedirectUrl(successRedirectUrl).withPrefilledCustomer(customer).execute();
		logger.info("RedirectFlow Id", redirectFlow.getId());
		logger.info("RedirectFlow URL", redirectFlow.getRedirectUrl());

		PluginHostedPaymentPageFormDescriptor pluginHostedPaymentPageFormDescriptor = new PluginHostedPaymentPageFormDescriptor(
				kbAccountId, redirectFlow.getRedirectUrl());
		return pluginHostedPaymentPageFormDescriptor;
	}

	private PrefilledCustomer buildCustomer(Iterable<PluginProperty> customFields) {
		PrefilledCustomer customer = new PrefilledCustomer();
		customer.withGivenName(PluginProperties.findPluginPropertyValue("customer_given_name", customFields)); // "Tim
		customer.withFamilyName(PluginProperties.findPluginPropertyValue("customer_family_name", customFields)); // "Rogers"
		customer.withEmail(PluginProperties.findPluginPropertyValue("customer_email", customFields));// "tim@gocardless.com"
		customer.withAddressLine1(PluginProperties.findPluginPropertyValue("customer_address_line1", customFields));// "338-346
																													// Goswell
																													// Road"
		customer.withCity(PluginProperties.findPluginPropertyValue("customer_city", customFields)); // "London"
		customer.withPostalCode(PluginProperties.findPluginPropertyValue("postal_code", customFields)); // "EC1V 7LQ"
		return customer;
	}

	@Override
	public GatewayNotification processNotification(String notification, Iterable<PluginProperty> properties,
			CallContext context) throws PaymentPluginApiException {
		return null;
	}

}
