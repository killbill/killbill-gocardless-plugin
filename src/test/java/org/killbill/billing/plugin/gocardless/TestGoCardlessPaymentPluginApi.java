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

import static org.testng.Assert.assertNotNull;

import java.util.UUID;

import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.plugin.api.HostedPaymentPageFormDescriptor;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;


public class TestGoCardlessPaymentPluginApi extends TestBase {
	
	   @Test(groups = "integration")
	    public void testHPP() throws PaymentPluginApiException {
	        final UUID kbAccountId = account.getId();
	        final ImmutableList<PluginProperty> properties = ImmutableList.of(
	                new PluginProperty("success_redirect_url", "https://developer.gocardless.com/example-redirect-uri/", false),
	                new PluginProperty("redirect_flow_description", "Kill Bill payment", false),
	                new PluginProperty("session_token", "killbill_token", false));
	        
	        HostedPaymentPageFormDescriptor hppDescriptor = goCardlessPaymentpluginApi.buildFormDescriptor(kbAccountId, ImmutableList.of(), properties, context);
	        assertNotNull(hppDescriptor);
	        assertNotNull(hppDescriptor.getFormUrl());
	    }	
}
