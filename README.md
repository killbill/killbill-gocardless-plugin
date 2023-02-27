# killbill-gocardless-plugin
![Maven Central](https://img.shields.io/maven-central/v/org.kill-bill.billing.plugin.java/gocardless-plugin?color=blue&label=Maven%20Central)

Kill Bill payment plugin that uses [Gocardless](https://gocardless.com/) as the payment gateway.

## Kill Bill compatibility

| Plugin version | Kill Bill version |
|---------------:|------------------:|
|          1.x.y |            0.24.z |


## Build

```
mvn clean install -DskipTests
```

## Installation

```
kpm install_java_plugin gocardless --from-source-file target/gocardless-plugin-*-SNAPSHOT.jar --destination /var/tmp/bundles
```

## Setup

A GoCardless access token is required in order to use GoCardless. You can [sign up](https://manage-sandbox.gocardless.com/signup) to create a sandbox account and obtain a token from [here](https://manage-sandbox.gocardless.com/developers/access-tokens/create).

## Configuration

Configure the plugin with the Gocardless token/environment as follows:

```
curl -v \
     -X POST \
     -u admin:password \
     -H 'X-Killbill-ApiKey: bob' \
     -H 'X-Killbill-ApiSecret: lazar' \
     -H 'X-Killbill-CreatedBy: admin' \
     -H 'Content-Type: text/plain' \
     -d 'org.killbill.billing.plugin.gocardless.gocardlesstoken=xxx
	 org.killbill.billing.plugin.gocardless.environment=xxx' \
     http://127.0.0.1:8080/1.0/kb/tenants/uploadPluginConfig/killbill-gocardless
```

where:

* `gocardlesstoken`: GoCardless access token obtained above
* `environment`: The Gocardless environment. Possible values are `SANDBOX`/`LIVE`. default value is `SANDBOX`

## Testing

1. Create a Kill Bill account for the customer (The following request uses the default Kill Bill API key and secret, change them if needed):

```
curl -v \
     -X POST \
     -u admin:password \
     -H 'X-Killbill-ApiKey: bob' \
     -H 'X-Killbill-ApiSecret: lazar' \
     -H 'X-Killbill-CreatedBy: tutorial' \
     -H 'Content-Type: application/json' \
     -d '{ "currency": "USD" }' \
     'http://127.0.0.1:8080/1.0/kb/accounts'
```

This returns the Kill Bill `accountId` in the `Location` header.
For example, in the following sample response, `17444cb7-bfa7-4f8c-a3c3-a98d31003566` is the account Id.

```
< Access-Control-Allow-Credentials: true
< Location: http://127.0.0.1:8080/1.0/kb/accounts/17444cb7-bfa7-4f8c-a3c3-a98d31003566
< Content-Type: application/json
```

2. Use the plugin `/checkout` API to create a redirect flow:

```
curl -v \
     -X POST \
     -u admin:password \
     -H "X-Killbill-ApiKey: bob" \
     -H "X-Killbill-ApiSecret: lazar" \
     -H 'X-Killbill-CreatedBy: tutorial' \
     -H "Content-Type: application/json" \
     'http://127.0.0.1:8080/plugins/killbill-gocardless/checkout?kbAccountId=<ACCOUNT_ID>'
```

This returns a response similar to the following:

```
{
  "formFields": [],
  "formMethod": "GET",
  "formUrl": "https://pay-sandbox.gocardless.com/billing/static/flow?id=BRF000103WH5G2459BAT7ET0Q0TMTG3Z",
  "kbAccountId": "5b4fc584-8af9-4d52-80ed-b46361cf1dc3",
  "properties": []
}
```

Copy the `formUrl` from the response and save it for further use.

3. Redirect the user to the `formUrl` and have them fill the form with their bank account details to set up a mandate. For testing you can enter the `formUrl` in a browser and enter the following bank account details (See [setting up a mandate](https://developer.gocardless.com/direct-debit/setting-up-a-mandate)):
  * Bank code: 026073150
  * Account number: 2715500356
  * Account type: checking
  
This redirects the user to a success page with the `redirect_flow_id` in the URL. Copy this `redirect_flow_id` and save it for further use.

4. Finally, complete the redirect flow using the `redirect_flow_id` obtained above:

```
curl -v \
     -X POST \
     -u admin:password \
     -H 'X-Killbill-ApiKey: bob' \
     -H 'X-Killbill-ApiSecret: lazar' \
     -H 'X-Killbill-CreatedBy: tutorial' \
     -H 'Content-Type: application/json' \
     -d '{
       "pluginName": "killbill-gocardless",
       "pluginInfo": {
         "properties": [
           {
             "key": "redirect_flow_id",
             "value": "<redirect_flow_id>"
           },
           {
             "key": "session_token",
             "value": "killbill_token"
           }
         ]
       }
     }' \
     'http://127.0.0.1:8080/1.0/kb/accounts/<ACCOUNT_ID>/paymentMethods?isDefault=true'
```

This adds the the mandate as a payment method in Kill Bill and returns the `paymentMethodId` in the Location header.

5. You can then trigger payments against that payment method:

```
curl -v \
     -X POST \
     -u admin:password \
     -H "X-Killbill-ApiKey: bob" \
     -H "X-Killbill-ApiSecret: lazar" \
     -H "X-Killbill-CreatedBy: tutorial" \
     -H "Content-Type: application/json" \
     --data-binary '{"transactionType":"PURCHASE","amount":"10"}' \
    'http://127.0.0.1:8080/1.0/kb/accounts/<ACCOUNT_ID>/payments'
```
This returns the `paymentId` in the `Location` header.

6. You can then obtain information about the payment as follows:

```
curl -v \
     -u admin:password \
     -H "X-Killbill-ApiKey: bob" \
     -H "X-Killbill-ApiSecret: lazar" \
    'http://127.0.0.1:8080/1.0/kb/payments/<PAYMENT_ID>?withPluginInfo=true'
```

7. If you do not want the plugin to be called, you can specify `withPluginInfo=false` as follows:

```
curl -v \
     -u admin:password \
     -H "X-Killbill-ApiKey: bob" \
     -H "X-Killbill-ApiSecret: lazar" \
    'http://127.0.0.1:8080/1.0/kb/payments/<PAYMENT_ID>?withPluginInfo=false'
```
