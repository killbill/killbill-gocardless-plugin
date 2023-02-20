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

import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.plugin.api.notification.PluginTenantConfigurableConfigurationHandler;

public class GoCardlessConfigurationHandler
		extends PluginTenantConfigurableConfigurationHandler<GoCardlessConfigProperties> {

	private final String region;

	public GoCardlessConfigurationHandler(final String region, final String pluginName,
			final OSGIKillbillAPI osgiKillbillAPI) {
		super(pluginName, osgiKillbillAPI);
		this.region = region;
	}

	@Override
	protected GoCardlessConfigProperties createConfigurable(final Properties properties) {
		return new GoCardlessConfigProperties(properties, region);
	}

}
