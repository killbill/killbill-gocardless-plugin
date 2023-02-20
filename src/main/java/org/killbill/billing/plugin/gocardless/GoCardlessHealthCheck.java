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

import java.util.Map;

import javax.annotation.Nullable;

import org.killbill.billing.osgi.api.Healthcheck;
import org.killbill.billing.tenant.api.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gocardless.GoCardlessClient;

public class GoCardlessHealthCheck implements Healthcheck {
	
	private static final Logger logger = LoggerFactory.getLogger(GoCardlessHealthCheck.class);
	
	private final GoCardlessConfigurationHandler goCardlessConfigurationHandler;
	
    public GoCardlessHealthCheck(final GoCardlessConfigurationHandler goCardlessConfigurationHandler) {
        this.goCardlessConfigurationHandler = goCardlessConfigurationHandler;
    }
    
    public HealthStatus getHealthStatus(@Nullable final Tenant tenant, @Nullable final Map properties) {
        if (tenant == null) {
            // The plugin is running
            return HealthStatus.healthy("Gocardless OK");
        } else {
            // Specifying the tenant lets you also validate the tenant configuration
            final GoCardlessConfigProperties goCardlessConfigProperties = goCardlessConfigurationHandler.getConfigurable(tenant.getId());
            return pingGocardless(goCardlessConfigProperties);
        }
    }
    
    private HealthStatus pingGocardless(final GoCardlessConfigProperties config) {
    	
	    try {
			final GoCardlessClient.Environment environment = config.getEnvironment().equalsIgnoreCase("live") ? GoCardlessClient.Environment.LIVE : GoCardlessClient.Environment.SANDBOX;
			GoCardlessClient.newBuilder(config.getGCAccessToken())
			.withEnvironment(environment).build();
			return HealthStatus.healthy("Gocardless OK");
		} catch (Exception e) {
			logger.warn("Healthcheck error", e);
			return HealthStatus.unHealthy("Gocardless error: " + e.getMessage());
		}
    }    

}
