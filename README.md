# killbill-gocardless-plugin
![Maven Central](https://img.shields.io/maven-central/v/org.kill-bill.billing.plugin.java/gocardless-plugin?color=blue&label=Maven%20Central)

Kill Bill payment plugin that uses [Gocardless](https://gocardless.com/) as the payment gateway.

The plugin supports two payment flows:

- **Direct Debit (mandate)** – Customer sets up a mandate via a redirect flow; payments are then taken on that mandate (typically 3–5 working days to settle).
- **Instant Bank Pay (IBP)** – One-off instant payments via [GoCardless Billing Requests](https://developer.gocardless.com/billing-requests/taking-an-instant-bank-payment). Funds are confirmed within minutes (UK Faster Payments, SEPA Instant, etc.).

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

## Instant Bank Pay (IBP)

Instant Bank Pay uses GoCardless Billing Requests for one-off instant payments (no mandate). The customer is redirected to authorise the payment with their bank; funds are confirmed within minutes.

**Flow:**

1. **Create a payment in Kill Bill** (e.g. for an invoice) so you have `kbPaymentId` and `kbTransactionId`.
2. **Start an instant checkout** – POST to the plugin with account, amount, currency, payment IDs, and a success redirect URL. The plugin creates a Billing Request and Billing Request Flow and returns a URL.
3. **Redirect the customer** to that URL. They complete the flow (select bank, authorise).
4. **On success**, GoCardless redirects to your `success_redirect_url`. Your page should call the plugin **complete** endpoint with the `billing_request_id` (from the redirect query string). The plugin fulfils the Billing Request and stores the GoCardless payment ID so Kill Bill can resolve it via `getPaymentInfo`.
5. **Kill Bill** can then fetch payment status with `GET /1.0/kb/payments/<PAYMENT_ID>?withPluginInfo=true` as usual.

### Create instant checkout (step 2)

```
curl -v -X POST \
     -u admin:password \
     -H "X-Killbill-ApiKey: bob" \
     -H "X-Killbill-ApiSecret: lazar" \
     -H "X-Killbill-CreatedBy: tutorial" \
     -H "Content-Type: application/json" \
     -d '{
       "kbAccountId": "<ACCOUNT_ID>",
       "amount": "25.00",
       "currency": "GBP",
       "kbPaymentId": "<KB_PAYMENT_ID>",
       "kbTransactionId": "<KB_TRANSACTION_ID>",
       "success_redirect_url": "https://your-app.com/payment/success",
       "description": "Order #12345"
     }' \
     'http://127.0.0.1:8080/plugins/killbill-gocardless/checkout/instant'
```

Response (201):

```json
{
  "formUrl": "https://pay-sandbox.gocardless.com/...",
  "formMethod": "GET",
  "billingRequestId": "BRQ...",
  "kbAccountId": "...",
  "kbPaymentId": "...",
  "kbTransactionId": "..."
}
```

Redirect the customer to `formUrl`.

### Complete instant payment (step 4)

When the customer returns to your `success_redirect_url`, GoCardless appends a query parameter such as `billing_request_id=BRQ...`. Your server or front end should call:

```
curl -v -X GET \
     -u admin:password \
     -H "X-Killbill-ApiKey: bob" \
     -H "X-Killbill-ApiSecret: lazar" \
     'http://127.0.0.1:8080/plugins/killbill-gocardless/instant/complete?billing_request_id=<BILLING_REQUEST_ID>'
```

Or POST with the same query parameter. Response (200):

```json
{
  "success": true,
  "billingRequestId": "BRQ...",
  "paymentId": "PM...",
  "kbAccountId": "...",
  "kbPaymentId": "..."
}
```

After this, Kill Bill’s `getPaymentInfo` (and thus `GET /1.0/kb/payments/<PAYMENT_ID>?withPluginInfo=true`) will return the instant payment status.

**Note:** IBP is supported for one-off payments in regions/schemes supported by GoCardless (e.g. UK Faster Payments, SEPA Instant). See [GoCardless Instant Bank Pay](https://developer.gocardless.com/billing-requests/taking-an-instant-bank-payment) for details.
