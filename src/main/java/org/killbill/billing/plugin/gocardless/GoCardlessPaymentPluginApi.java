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
import org.killbill.billing.plugin.util.KillBillMoney;
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
import com.gocardless.resources.Mandate;
import com.gocardless.resources.Payment;
import com.gocardless.resources.RedirectFlow;
import com.gocardless.services.RedirectFlowService.RedirectFlowCreateRequest.PrefilledCustomer;
import com.google.common.collect.ImmutableList;
import com.google.gson.annotations.SerializedName;

import org.killbill.billing.plugin.api.core.PluginCustomField;
import org.killbill.billing.ObjectType;

public class GoCardlessPaymentPluginApi implements PaymentPluginApi {

	private static final Logger logger = LoggerFactory.getLogger(GoCardlessPaymentPluginApi.class);
	private OSGIKillbillAPI killbillAPI;
	private Clock clock;
	private static String GC_ACCESS_TOKEN_PROPERTY = "GC_ACCESS_TOKEN";

	private GoCardlessClient client;

	public GoCardlessPaymentPluginApi(final OSGIKillbillAPI killbillAPI, final Clock clock) {
		this.killbillAPI = killbillAPI;
		this.clock = clock;
		client = GoCardlessClient.newBuilder(System.getenv(GC_ACCESS_TOKEN_PROPERTY))
				.withEnvironment(GoCardlessClient.Environment.SANDBOX).build();
	}

	@Override
	public PaymentTransactionInfoPlugin authorizePayment(UUID kbAccountId, UUID kbPaymentId, UUID kbTransactionId,
			UUID kbPaymentMethodId, BigDecimal amount, Currency currency, Iterable<PluginProperty> properties,
			CallContext context) throws PaymentPluginApiException {
		PaymentTransactionInfoPlugin paymentTransactionInfoPlugin = new GoCardlessPaymentTransactionInfoPlugin(kbPaymentId, kbTransactionId,
				TransactionType.AUTHORIZE, amount, currency, PaymentPluginStatus.CANCELED, null, 
				null, null, null, new DateTime(), null, null);
		return paymentTransactionInfoPlugin;
	}

	@Override
	public PaymentTransactionInfoPlugin capturePayment(UUID kbAccountId, UUID kbPaymentId, UUID kbTransactionId,
			UUID kbPaymentMethodId, BigDecimal amount, Currency currency, Iterable<PluginProperty> properties,
			CallContext context) throws PaymentPluginApiException {
		PaymentTransactionInfoPlugin paymentTransactionInfoPlugin = new GoCardlessPaymentTransactionInfoPlugin(kbPaymentId, kbTransactionId,
				TransactionType.CAPTURE, amount, currency, PaymentPluginStatus.CANCELED, null, 
				null, null, null, new DateTime(), null, null);
		return paymentTransactionInfoPlugin;
	}

	@Override
	public PaymentTransactionInfoPlugin purchasePayment(UUID kbAccountId, UUID kbPaymentId, UUID kbTransactionId,
			UUID kbPaymentMethodId, BigDecimal amount, Currency currency, Iterable<PluginProperty> properties,
			CallContext context) throws PaymentPluginApiException {
		logger.info("purchasePayment, kbAccountId=" + kbAccountId);
		PaymentTransactionInfoPlugin paymentTransactionInfoPlugin;
		String mandate = getMandateId(kbAccountId, context); // retrieve mandateId from Kill Bill tables
		logger.info("MandateId="+mandate);
		if (mandate != null) {
			logger.info("Processing payment");
			try {
				String idempotencyKey = PluginProperties.findPluginPropertyValue("idempotencykey", properties);
				com.gocardless.services.PaymentService.PaymentCreateRequest.Currency goCardlessCurrency = convertKillBillCurrencyToGoCardlessCurrency(
						currency);
				Payment payment = client.payments().create()
						.withAmount(Math.toIntExact(KillBillMoney.toMinorUnits(currency.toString(), amount)))
						.withCurrency(goCardlessCurrency).withLinksMandate(mandate).withIdempotencyKey(idempotencyKey)
						.withMetadata("kbPaymentId", kbPaymentId.toString()).withMetadata("kbTransactionId", kbTransactionId.toString()) //added for getPaymentInfo
						.execute();
				List<PluginProperty> outputProperties = new ArrayList<PluginProperty>();
				outputProperties.add(new PluginProperty("paymentId", payment.getId(), false));
				paymentTransactionInfoPlugin = new GoCardlessPaymentTransactionInfoPlugin(kbPaymentId, kbTransactionId,
						TransactionType.PURCHASE, amount, currency, PaymentPluginStatus.PROCESSED, null, null,
						String.valueOf(payment.getId()), null, new DateTime(), new DateTime(payment.getCreatedAt()),
						outputProperties);
				logger.info("Payment processed, PaymentId="+payment.getId());
			} catch (GoCardlessApiException e) {
				paymentTransactionInfoPlugin = new GoCardlessPaymentTransactionInfoPlugin(kbPaymentId, kbTransactionId,
						TransactionType.PURCHASE, amount, currency, PaymentPluginStatus.ERROR, e.getErrorMessage(),
						String.valueOf(e.getCode()), null, null, new DateTime(), null, null);

				logger.warn("Error occured in purchasePayment", e.getType(), e);
			}
		} else {
			logger.warn("Unable to fetch mandate, so cannot process payment");
			paymentTransactionInfoPlugin = new GoCardlessPaymentTransactionInfoPlugin(kbPaymentId, kbTransactionId,
					TransactionType.PURCHASE, amount, currency, PaymentPluginStatus.CANCELED, null, 
					null, null, null, new DateTime(), null, null);
		}
		return paymentTransactionInfoPlugin;
	}

	/**
	 * Retrieves mandateId from Kill Bill tables
	 * 
	 * @param kbAccountId
	 * @param context
	 * @return
	 */
	private String getMandateId(UUID kbAccountId, TenantContext context) {
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
	 * Converts Kill Bill currency to GoCardless currency
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

	/**
	 * Converts GoCardless currency to Kill Bill currency
	 * 
	 * @param currency
	 * @return
	 */
	private Currency convertGoCardlessCurrencyToKillBillCurrency(com.gocardless.resources.Payment.Currency currency) {

		switch (currency) {
		case USD:
			return Currency.USD;
		case AUD:
			return Currency.AUD;
		case CAD:
			return Currency.CAD;
		case DKK:
			return Currency.DKK;
		case EUR:
			return Currency.EUR;
		case GBP:
			return Currency.GBP;
		case NZD:
			return Currency.NZD;
		case SEK:
			return Currency.SEK;
		default:
			return null;

		}
	}

	@Override
	public PaymentTransactionInfoPlugin voidPayment(UUID kbAccountId, UUID kbPaymentId, UUID kbTransactionId,
			UUID kbPaymentMethodId, Iterable<PluginProperty> properties, CallContext context)
			throws PaymentPluginApiException {
		PaymentTransactionInfoPlugin paymentTransactionInfoPlugin = new GoCardlessPaymentTransactionInfoPlugin(kbPaymentId, kbTransactionId,
				TransactionType.VOID, null, null, PaymentPluginStatus.CANCELED, null, 
				null, null, null, new DateTime(), null, null);
		return paymentTransactionInfoPlugin;

	}

	@Override
	public PaymentTransactionInfoPlugin creditPayment(UUID kbAccountId, UUID kbPaymentId, UUID kbTransactionId,
			UUID kbPaymentMethodId, BigDecimal amount, Currency currency, Iterable<PluginProperty> properties,
			CallContext context) throws PaymentPluginApiException {

		PaymentTransactionInfoPlugin paymentTransactionInfoPlugin = new GoCardlessPaymentTransactionInfoPlugin(kbPaymentId, kbTransactionId,
				TransactionType.CREDIT, amount, currency, PaymentPluginStatus.CANCELED, null, 
				null, null, null, new DateTime(), null, null);
		return paymentTransactionInfoPlugin;
	}

	@Override
	public PaymentTransactionInfoPlugin refundPayment(UUID kbAccountId, UUID kbPaymentId, UUID kbTransactionId,
			UUID kbPaymentMethodId, BigDecimal amount, Currency currency, Iterable<PluginProperty> properties,
			CallContext context) throws PaymentPluginApiException {
		PaymentTransactionInfoPlugin paymentTransactionInfoPlugin = new GoCardlessPaymentTransactionInfoPlugin(kbPaymentId, kbTransactionId,
				TransactionType.REFUND, amount, currency, PaymentPluginStatus.CANCELED, null, 
				null, null, null, new DateTime(), null, null);
		return paymentTransactionInfoPlugin;
	}

	public List<PaymentTransactionInfoPlugin> getPaymentInfo(UUID kbAccountId, UUID kbPaymentId,
			Iterable<PluginProperty> properties, TenantContext context) throws PaymentPluginApiException {
		
		List<PaymentTransactionInfoPlugin> paymentTransactionInfoPluginList = new ArrayList<>();
		String mandateId = getMandateId(kbAccountId, context) ;
		Mandate mandate = client.mandates().get(mandateId).execute(); //get GoCardless Mandate object
		String customerId = mandate.getLinks().getCustomer(); //retrieve customer id from mandate
		
		Iterable<Payment> payments = client.payments().all().withCustomer(customerId).execute(); //get all payments related to customer
		
		for (Payment payment : payments) {
			String kbPaymentIdFromPayment = payment.getMetadata().get("kbPaymentId"); //get kbPaymentId from metadata in payment
			if(kbPaymentIdFromPayment != null && kbPaymentId.toString().equals(kbPaymentIdFromPayment)) {
				Currency killBillCurrency = convertGoCardlessCurrencyToKillBillCurrency(payment.getCurrency());
				PaymentPluginStatus status = convertGoCardlessToKillBillStatus(payment.getStatus());
				String kbTransactionPaymentIdStr = payment.getMetadata().get("kbTransactionId"); 
				UUID kbTransactionPaymentId = kbTransactionPaymentIdStr !=null?UUID.fromString(kbTransactionPaymentIdStr):null;
				List<PluginProperty> outputProperties = new ArrayList<PluginProperty>();
				outputProperties.add(new PluginProperty("mandateId",mandateId,false)); //arbitrary data to be returned to the caller
				outputProperties.add(new PluginProperty("customerId",customerId,false));  //arbitrary data to be returned to the caller
				outputProperties.add(new PluginProperty("gocardlessstatus",payment.getStatus(),false)); //arbitrary data to be returned to the caller
				GoCardlessPaymentTransactionInfoPlugin paymentTransactionInfoPlugin = new GoCardlessPaymentTransactionInfoPlugin(
						kbPaymentId, kbTransactionPaymentId, TransactionType.PURCHASE, new BigDecimal(payment.getAmount()), killBillCurrency,
						status, null, null, String.valueOf(payment.getId()), null, new DateTime(),
						new DateTime(payment.getCreatedAt()), outputProperties); 
				logger.info("Created paymentTransactionInfoPlugin {}",paymentTransactionInfoPlugin);
				paymentTransactionInfoPluginList.add(paymentTransactionInfoPlugin);
			}
		}
		
		return paymentTransactionInfoPluginList;
	}

	/**
	 * Converts GoCardless status to Kill Bill status
	 * TODO: confirm if the status is correct
	 * @param status
	 * @return
	 */
	private PaymentPluginStatus convertGoCardlessToKillBillStatus(Payment.Status status) {
		switch (status) {
		case PENDING_CUSTOMER_APPROVAL://waiting for the customer to approve this payment
		case PENDING_SUBMISSION: //the payment has been created, but not yet submitted to the banks TODO: Should this be translated to PaymentPluginStatus.PROCESSED? 
		case SUBMITTED: //the payment has been submitted to the banks TODO: Should this be translated to PaymentPluginStatus.PROCESSED?
			return PaymentPluginStatus.PENDING; 
		case CONFIRMED: //the payment has been confirmed as collected
		case PAID_OUT: // the payment has been included in a payout
			return PaymentPluginStatus.PROCESSED; 
		case CANCELLED: //the payment has been cancelled TODO: Should this be translated to PaymentPluginStatus.ERROR?
		case CUSTOMER_APPROVAL_DENIED: //the customer has denied approval for the payment TODO: Should this be translated to PaymentPluginStatus.ERROR?
			return PaymentPluginStatus.CANCELED;
		case FAILED: // the payment failed to be processed. 
			return PaymentPluginStatus.ERROR;
		case CHARGED_BACK: // the payment has been charged back TODO: Should this be translated to PaymentPluginStatus.ERROR?
			return PaymentPluginStatus.PROCESSED;
		default:
			return PaymentPluginStatus.UNDEFINED;
		}
	}

	@Override
	public Pagination<PaymentTransactionInfoPlugin> searchPayments(String searchKey, Long offset, Long limit,
			Iterable<PluginProperty> properties, TenantContext context) throws PaymentPluginApiException {
		
		 throw new PaymentPluginApiException(null, "SEARCH: unsupported operation"); //TODO: Causes NullPointerException, to be fixed later
	}

	@Override
	public void addPaymentMethod(UUID kbAccountId, UUID kbPaymentMethodId, PaymentMethodPlugin paymentMethodProps,
			boolean setDefault, Iterable<PluginProperty> properties, CallContext context)
			throws PaymentPluginApiException {
		logger.info("addPaymentMethod, kbAccountId=" + kbAccountId);
		final Iterable<PluginProperty> allProperties = PluginProperties.merge(paymentMethodProps.getProperties(),
				properties);
		String redirectFlowId = PluginProperties.findPluginPropertyValue("redirect_flow_id", allProperties); // retrieve the redirect flow id
		String sessionToken = PluginProperties.findPluginPropertyValue("session_token", allProperties);

		try {
			// Use the redirect flow id to "complete" the GoCardless flow
			RedirectFlow redirectFlow = client.redirectFlows().complete(redirectFlowId).withSessionToken(sessionToken)
					.execute();

			String mandateId = redirectFlow.getLinks().getMandate(); // obtain mandate id from the redirect flow
			logger.info("MandateId=" + mandateId);

			try {
				// save Mandate id in the Kill Bill database
				killbillAPI.getCustomFieldUserApi().addCustomFields(ImmutableList.of(new PluginCustomField(kbAccountId,
						ObjectType.ACCOUNT, "GOCARDLESS_MANDATE_ID", mandateId, clock.getUTCNow())), context);
			} catch (CustomFieldApiException e) {
				logger.warn("Error occured while saving mandate id", e);
				throw new PaymentPluginApiException("Error occured while saving mandate id", e);
			}

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
		return new GoCardlessPaymentMethodPlugin(kbPaymentMethodId, null,false, null);
	}

	@Override
	public void setDefaultPaymentMethod(UUID kbAccountId, UUID kbPaymentMethodId, Iterable<PluginProperty> properties,
			CallContext context) throws PaymentPluginApiException {
	}

	@Override
	public List<PaymentMethodInfoPlugin> getPaymentMethods(UUID kbAccountId, boolean refreshFromGateway,
			Iterable<PluginProperty> properties, CallContext context) throws PaymentPluginApiException {
		
		List<PaymentMethodInfoPlugin> result = new ArrayList<PaymentMethodInfoPlugin>();
		return result;
	}

	@Override
	public Pagination<PaymentMethodPlugin> searchPaymentMethods(String searchKey, Long offset, Long limit,
			Iterable<PluginProperty> properties, TenantContext context) throws PaymentPluginApiException {
		throw new PaymentPluginApiException(null, "SEARCH: unsupported operation");//TODO: Causes NullPointerException, to be fixed later
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
		String successRedirectUrl = PluginProperties.findPluginPropertyValue("success_redirect_url", properties); // "https://developer.gocardless.com/example-redirect-uri/"; this is the URL to which GoCardless redirects to after users set up the mandate
																													
		String redirectFlowDescription = PluginProperties.findPluginPropertyValue("redirect_flow_description",properties);
		String sessionToken = PluginProperties.findPluginPropertyValue("session_token", properties); // "dummy_session_token"

		PrefilledCustomer customer = buildCustomer(customFields);// build a PrefilledCuctomer object from custom fields if present

		RedirectFlow redirectFlow = client.redirectFlows().create().withDescription(redirectFlowDescription)
				.withSessionToken(sessionToken).withSuccessRedirectUrl(successRedirectUrl)
				.withPrefilledCustomer(customer).execute();
		logger.info("RedirectFlow Id=" + redirectFlow.getId());
		logger.info("RedirectFlow URL=" + redirectFlow.getRedirectUrl());

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
		throw new PaymentPluginApiException("INTERNAL", "#processNotification not yet implemented, please contact support@killbill.io");
	}

}
