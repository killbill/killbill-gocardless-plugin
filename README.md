# killbill-gocardless-example-plugin
GoCardless Payment Plugin (tutorial)

## Build

```
​mvn clean install -DskipTests
```

## Installation

```
kpm install_java_plugin gocardless --from-source-file target/gocardless-plugin-*-SNAPSHOT.jar --destination /var/tmp/bundles
```

## Testing

Before starting Kill Bill, set the following environment variable (token can be found at https://manage-sandbox.gocardless.com/developers):

```
export GC_ACCESS_TOKEN=<ACCESS_TOKEN>
```

The flow to create a mandate is as follows:

1. Create a Kill Bill account for the customer
```
curl -v \
     -X POST \
     -u admin:password \
     -H 'X-Killbill-ApiKey: bob' \
     -H 'X-Killbill-ApiSecret: lazar' \
     -H 'X-Killbill-CreatedBy: tutorial' \
     -H 'Content-Type: application/json' \
     -d '{ "currency": "GBP" }' \
     'http://127.0.0.1:8080/1.0/kb/accounts'
```
This returns the Kill Bill `accountId` in the `Location` header.

2. Use the plugin `/checkout` API to create a redirect flow, generating a URL which you can send the customer to in order to have them set up a mandate
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
This returns a `formUrl`. Have the customer fill the form with the bank account details.

3. Finally, complete the redirect flow by adding the mandate as a payment method in Kill Bill
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

4. You can then trigger payments against that payment method
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

5. You can then obtain information about the payment as follows:
```
curl -v \
     -u admin:password \
     -H "X-Killbill-ApiKey: bob" \
     -H "X-Killbill-ApiSecret: lazar" \
    'http://127.0.0.1:8080/1.0/kb/payments/<PAYMENT_ID>?withPluginInfo=true'
```

6. If you do not want the plugin to be called, you can specify `withPluginInfo=false` as follows:
```
curl -v \
     -u admin:password \
     -H "X-Killbill-ApiKey: bob" \
     -H "X-Killbill-ApiSecret: lazar" \
    'http://127.0.0.1:8080/1.0/kb/payments/<PAYMENT_ID>?withPluginInfo=false'
```
