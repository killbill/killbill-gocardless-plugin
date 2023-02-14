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
import java.util.Properties;


public class GoCardlessConfigProperties {
	
	private static final String PROPERTY_PREFIX = "org.killbill.billing.plugin.gocardless.";

	public static final String GOCARDLESS_ACCESS_TOKEN_KEY = "GOCARDLESS_ACCESS_TOKEN";
	public static final String GOCARDLESS_ENVIRONMENT_KEY = "GOCARDLESS_ENVIRONMENT";
	
	private final String goCardlessAccessToken;
	private final String environment;
	
	public GoCardlessConfigProperties(final Properties properties, final String region) {
		this.goCardlessAccessToken = properties.getProperty(PROPERTY_PREFIX + "gocardlesstoken");
		this.environment = properties.getProperty(PROPERTY_PREFIX + "environment", "sandbox"); //defaults to sandbox
	}	
	
	public String getGCAccessToken() {
		if (goCardlessAccessToken == null || goCardlessAccessToken.isEmpty()) {
			return getClient(GOCARDLESS_ACCESS_TOKEN_KEY, null);
		}
		return goCardlessAccessToken;
	}
	
	public String getEnvironment() {
		if (environment == null || environment.isEmpty()) {
			return getClient(GOCARDLESS_ENVIRONMENT_KEY, null);
		}
		return environment;
	}	
	
	private String getClient(String envKey, String defaultValue) {
		Map<String, String> env = System.getenv();

		String value = env.get(envKey);

		if (value == null || value.isEmpty()) {
			return defaultValue;
		}

		return value;
	}	

}
