package com.stripe.sample;

import java.nio.file.Paths;

import java.util.HashMap;
import java.util.Map;

import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.port;
import static spark.Spark.staticFiles;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import com.stripe.Stripe;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.exception.*;
import com.stripe.net.Webhook;
import com.stripe.net.ApiResource;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionCreateParams.LineItem;
import com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData;
import com.stripe.param.checkout.SessionCreateParams.PaymentMethodType;
import com.stripe.param.checkout.SessionCreateParams.SubscriptionData;
import com.stripe.model.EventDataObjectDeserializer;

import io.github.cdimascio.dotenv.Dotenv;

public class Server {
    private static Gson gson = new Gson();

    static class PostBody {
        @SerializedName("donation")
        int donation;

        public int getDonation() {
            return donation;
        }
    }

    public static void main(String[] args) {
        port(4242);

        Dotenv dotenv = Dotenv.load();

        Stripe.apiKey = dotenv.get("STRIPE_SECRET_KEY");

        staticFiles.externalLocation(
                Paths.get(Paths.get("").toAbsolutePath().toString(), dotenv.get("STATIC_DIR")).normalize().toString());

        get("/publishable-key", (request, response) -> {
            response.type("application/json");

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("publishableKey", dotenv.get("STRIPE_PUBLISHABLE_KEY"));
            return gson.toJson(responseData);
        });

        // Fetch the Checkout Session to display the JSON result on the success page
        get("/checkout-session", (request, response) -> {
            response.type("application/json");

            String sessionId = request.queryParams("sessionId");
            Session session = Session.retrieve(sessionId);

            return gson.toJson(session);
        });

        post("/create-checkout-session", (request, response) -> {
            response.type("application/json");
            PostBody postBody = gson.fromJson(request.body(), PostBody.class);

            String domainUrl = dotenv.get("DOMAIN");
            String priceId = dotenv.get("SUBSCRIPTION_PRICE_ID");
            String donationProduct = dotenv.get("DONATION_PRODUCT_ID");

            // Create subscription
            SessionCreateParams.Builder builder = new SessionCreateParams.Builder();

            builder.setSuccessUrl(domainUrl + "/success.html?session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(domainUrl + "/cancel.html").setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setAllowPromotionCodes(true)
                    .addPaymentMethodType(PaymentMethodType.CARD);
            // Add a line item for the sticker the Customer is purchasing
            LineItem item = new LineItem.Builder().setQuantity(new Long(1)).putExtraParam("price", priceId).build();
            builder.addLineItem(item);

            if (postBody.getDonation() > 0) {
                PriceData priceData = new PriceData.Builder().setUnitAmount(new Long(postBody.getDonation()))
                        .setCurrency("usd").setProduct(donationProduct).build();
                LineItem donationItem = new LineItem.Builder().setQuantity(new Long(1)).setPriceData(priceData).build();
                builder.addLineItem(donationItem);
            }

            SessionCreateParams createParams = builder.build();
            Session session = Session.create(createParams);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("checkoutSessionId", session.getId());
            return gson.toJson(responseData);
        });

        post("/webhook", (request, response) -> {
            String payload = request.body();
            String sigHeader = request.headers("Stripe-Signature");
            String endpointSecret = dotenv.get("STRIPE_WEBHOOK_SECRET");

            Event event = null;

            try {
                event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
            } catch (SignatureVerificationException e) {
                // Invalid signature
                response.status(400);
                return "";
            }

            switch (event.getType()) {
                case "checkout.session.completed":
                    EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
                    Session session = ApiResource.GSON.fromJson(deserializer.getRawJson(), Session.class);
                    Customer customer = Customer.retrieve(session.getCustomer());

                    if (session.getDisplayItems().size() > 0
                            && session.getDisplayItems().get(0).getAmount().equals(new Long(300))) {
                        System.out.println("🔔  Customer is subscribed and bought an e-book! Send the e-book to "
                                + customer.getEmail());
                    } else {
                        System.out.println("🔔  Customer is subscribed but did not buy an e-book.");

                    }
                    break;
                default:
                    // Other event type
                    System.out.println("Received event " + event.getType());
                    break;
            }

            response.status(200);
            return "";
        });
    }
}