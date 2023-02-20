/*
 * Copyright 2020-2023 Equinix, Inc
 * Copyright 2014-2023 The Billing Project, LLC
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

import java.util.Properties;
import java.util.UUID;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.plugin.TestUtils;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.clock.ClockMock;
import org.mockito.Mockito;

import org.testng.annotations.BeforeMethod;


import com.gocardless.GoCardlessClient;

public class TestBase {

    private static final String PROPERTIES_FILE_NAME = "gocardless.properties";

    public static final Currency DEFAULT_CURRENCY = Currency.USD;
    public static final String DEFAULT_COUNTRY = "US";

    protected ClockMock clock;
    protected CallContext context;
    protected Account account;
    protected GoCardlessPaymentPluginApi goCardlessPaymentpluginApi;
    protected OSGIKillbillAPI killbillApi;
    protected CustomFieldUserApi customFieldUserApi;
    protected GoCardlessConfigurationHandler goCardlessconfigurationHandler;
    protected GoCardlessClient goCardlessClient;

    @BeforeMethod(groups = {"slow", "integration"})
    public void setUp() throws Exception {

        clock = new ClockMock();

        context = Mockito.mock(CallContext.class);
        Mockito.when(context.getTenantId()).thenReturn(UUID.randomUUID());

        account = TestUtils.buildAccount(DEFAULT_CURRENCY, DEFAULT_COUNTRY);
        Mockito.when(account.getEmail()).thenReturn(UUID.randomUUID().toString() + "@example.com");
        killbillApi = TestUtils.buildOSGIKillbillAPI(account);
        customFieldUserApi = Mockito.mock(CustomFieldUserApi.class);
        Mockito.when(killbillApi.getCustomFieldUserApi()).thenReturn(customFieldUserApi);

        TestUtils.buildPaymentMethod(account.getId(), account.getPaymentMethodId(), GoCardlessActivator.PLUGIN_NAME, killbillApi);

        goCardlessconfigurationHandler = new GoCardlessConfigurationHandler("", GoCardlessActivator.PLUGIN_NAME, killbillApi);

        goCardlessPaymentpluginApi = new GoCardlessPaymentPluginApi(goCardlessconfigurationHandler,
                                                            killbillApi,
                                                            clock);

        TestUtils.updateOSGIKillbillAPI(killbillApi, goCardlessPaymentpluginApi);

    }

    @BeforeMethod(groups = "integration")
    public void setUpIntegration() throws Exception {
        Properties properties = new Properties();
        try {
            properties = TestUtils.loadProperties(PROPERTIES_FILE_NAME);
        } catch (final RuntimeException ignored) {
            // Look up environment variables instead
            properties.put("org.killbill.billing.plugin.gocardless.gocardlesstoken", System.getenv("GOCARDLESS_ACCESS_TOKEN"));
            properties.put("org.killbill.billing.plugin.gocardless.environment", System.getenv("GOCARDLESS_ENVIRONMENT"));
        }
        final GoCardlessConfigProperties goCardlessConfigProperties = new GoCardlessConfigProperties(properties, "");
        goCardlessconfigurationHandler.setDefaultConfigurable(goCardlessConfigProperties);
        
        final GoCardlessClient.Environment environment = goCardlessConfigProperties.getEnvironment().equalsIgnoreCase("live") ? GoCardlessClient.Environment.LIVE : GoCardlessClient.Environment.SANDBOX;
        goCardlessClient = GoCardlessClient.newBuilder(goCardlessConfigProperties.getGCAccessToken())
		.withEnvironment(environment).build();
    }

}
