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

import org.killbill.billing.osgi.api.OSGIPluginProperties;
import org.killbill.billing.osgi.libs.killbill.KillbillActivatorBase;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.plugin.core.config.PluginEnvironmentConfig;
import org.killbill.billing.plugin.core.resources.jooby.PluginApp;
import org.killbill.billing.plugin.core.resources.jooby.PluginAppBuilder;
import org.osgi.framework.BundleContext;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import java.util.Hashtable;

public class GoCardlessActivator extends KillbillActivatorBase {
	
	//This is the plugin name and is used by Kill Bill to route payment to the appropriate payment plugin
	public static final String PLUGIN_NAME = "killbill-gocardless"; 
	
	private GoCardlessConfigurationHandler goCardlessConfigurationHandler;

	@Override
    public void start(final BundleContext context) throws Exception {
        super.start(context);
        final String region = PluginEnvironmentConfig.getRegion(configProperties.getProperties());
        goCardlessConfigurationHandler = new GoCardlessConfigurationHandler(region, PLUGIN_NAME, killbillAPI);
        
        //set default config properties
        final GoCardlessConfigProperties globalConfiguration = goCardlessConfigurationHandler
				.createConfigurable(configProperties.getProperties());
        goCardlessConfigurationHandler.setDefaultConfigurable(globalConfiguration);        
        
        final GoCardlessPaymentPluginApi pluginApi = new GoCardlessPaymentPluginApi(goCardlessConfigurationHandler,killbillAPI,clock.getClock());
        registerPaymentPluginApi(context, pluginApi);

        // Register the servlet, which is used as the entry point to generate the Hosted Payment Pages redirect url
        final PluginApp pluginApp = new PluginAppBuilder(PLUGIN_NAME, killbillAPI, dataSource, super.clock, configProperties)
                .withRouteClass(GoCardlessCheckoutServlet.class)
                .withService(pluginApi)
                .withService(clock)
                .build();
        final HttpServlet goCardlessServlet = PluginApp.createServlet(pluginApp);
        registerServlet(context, goCardlessServlet);
    }
	
    private void registerPaymentPluginApi(final BundleContext context, final PaymentPluginApi api) {
        final Hashtable<String, String> props = new Hashtable<String, String>();
        props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, PLUGIN_NAME);
        registrar.registerService(context, PaymentPluginApi.class, api, props);
    }

    private void registerServlet(final BundleContext context, final HttpServlet servlet) {
        final Hashtable<String, String> props = new Hashtable<String, String>();
        props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, PLUGIN_NAME);
        registrar.registerService(context, Servlet.class, servlet, props);
    }
}
