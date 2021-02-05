package org.killbill.billing.plugin.gocardless;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.plugin.api.payment.PluginPaymentTransactionInfoPlugin;

public class GoCardlessPaymentTransactionInfoPlugin extends PluginPaymentTransactionInfoPlugin{

	public GoCardlessPaymentTransactionInfoPlugin(UUID kbPaymentId, UUID kbTransactionPaymentPaymentId,
			TransactionType transactionType, BigDecimal amount, Currency currency, PaymentPluginStatus pluginStatus,
			String gatewayError, String gatewayErrorCode, String firstPaymentReferenceId,
			String secondPaymentReferenceId, DateTime createdDate, DateTime effectiveDate,
			List<PluginProperty> properties) {
		super(kbPaymentId, kbTransactionPaymentPaymentId, transactionType, amount, currency, pluginStatus, gatewayError,
				gatewayErrorCode, firstPaymentReferenceId, secondPaymentReferenceId, createdDate, effectiveDate, properties);
	}

}
