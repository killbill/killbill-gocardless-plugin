package org.killbill.billing.plugin.gocardless;

import java.util.List;
import java.util.UUID;

import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.api.payment.PluginPaymentMethodInfoPlugin;
import org.killbill.billing.plugin.api.payment.PluginPaymentMethodPlugin;

public class GoCardlessPaymentMethodPlugin extends PluginPaymentMethodPlugin  {

	public GoCardlessPaymentMethodPlugin(UUID kbPaymentMethodId, String externalPaymentMethodId,
			boolean isDefaultPaymentMethod, List<PluginProperty> properties) {
		super(kbPaymentMethodId, externalPaymentMethodId, isDefaultPaymentMethod, properties);
	}



}
