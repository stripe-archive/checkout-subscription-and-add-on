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
import com.stripe.param.checkout.SessionCreateParams.PaymentMethodType;
import com.stripe.param.checkout.SessionCreateParams.SubscriptionData;
import com.stripe.model.EventDataObjectDeserializer;

public class Server {
    private static Gson gson = new Gson();

    static class PostBody {
        @SerializedName("isBuyingSticker")
        Boolean isBuyingSticker;

        public Boolean getIsBuyingSticker() {
            return isBuyingSticker;
        }
    }

    public static void main(String[] args) {
        port(4242);
        Stripe.apiKey = System.getenv("STRIPE_SECRET_KEY");

        staticFiles.externalLocation(
                Paths.get(Paths.get("").toAbsolutePath().getParent().getParent().toString() + "/client")
                        .toAbsolutePath().toString());

        get("/public-key", (request, response) -> {
            response.type("application/json");

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("publicKey", System.getenv("STRIPE_PUBLIC_KEY"));
            return gson.toJson(responseData);
        });

        post("/create-checkout-session", (request, response) -> {
            response.type("application/json");
            PostBody postBody = gson.fromJson(request.body(), PostBody.class);

            String domainUrl = System.getenv("DOMAIN");
            String planId = System.getenv("SUBSCRIPTION_PLAN_ID");

            // Create subscription
            SessionCreateParams.Builder builder = new SessionCreateParams.Builder();
            SubscriptionData.Item plan = new SubscriptionData.Item.Builder().setPlan(planId).build();
            SubscriptionData subscriptionData = new SubscriptionData.Builder().addItem(plan).build();

            builder.setSuccessUrl(domainUrl + "/success.html").setCancelUrl(domainUrl + "/cancel.html")
                    .setSubscriptionData(subscriptionData).addPaymentMethodType(PaymentMethodType.CARD);

            if (postBody.getIsBuyingSticker()) {
                // Add a line item for the sticker the Customer is purchasing
                LineItem item = new LineItem.Builder().setName("Pasha e-book").setAmount(new Long(300))
                        .setQuantity(new Long(1)).setCurrency("usd").build();
                builder.addLineItem(item);
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
            String endpointSecret = System.getenv("STRIPE_WEBHOOK_SECRET");

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