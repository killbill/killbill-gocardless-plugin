package org.killbill.billing.plugin.gocardless;

import java.util.Hashtable;

import org.killbill.billing.osgi.api.OSGIPluginProperties;
import org.killbill.billing.osgi.libs.killbill.KillbillActivatorBase;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;

import org.osgi.framework.BundleContext;


public class GoCardlessActivator extends KillbillActivatorBase{
	
	public static final String PLUGIN_NAME = "killbill-gocardless";

	@Override
    public void start(final BundleContext context) throws Exception {
        super.start(context);
        final GoCardlessPaymentPluginApi pluginApi = new GoCardlessPaymentPluginApi(killbillAPI,clock.getClock());
        registerPaymentPluginApi(context, pluginApi);
    }
	
    private void registerPaymentPluginApi(final BundleContext context, final PaymentPluginApi api) {
        final Hashtable<String, String> props = new Hashtable<String, String>();
        props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, PLUGIN_NAME);
        registrar.registerService(context, PaymentPluginApi.class, api, props);
    }
}
