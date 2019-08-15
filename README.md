# Using Checkout to create a subscription

[Checkout](https://stripe.com/docs/payments/checkout) lets you create a pre-built payment page hosted on Stripe that handles internationalization, displaying error messages, and rendering a responsive design.

This sample uses Checkout to start a monthly subscription for a new customer, with the option to add a one-off charge to the order.

[View](https://508st.sse.codesandbox.io/) or [fork](https://codesandbox.io/s/checkout-subscription-with-add-on-508st) the demo on CodeSandbox.

[<img src="./checkout-example.gif" alt="Example of Stripe Checkout" align="center">](https://508st.sse.codesandbox.io/)

This sample shows you how to:
* Start a subscription using the Checkout Session API 🌀
* Optionally add a one-off line item to the payment 💵
* Use a webhook to fulfill the order ️️✨

## How to run locally
You will need a Stripe account with its own set of [API keys](https://stripe.com/docs/development#api-keys) and an existing [Plan](https://stripe.com/docs/billing/subscriptions/creating#plans) created either in your Stripe Dashboard or through the [API](https://stripe.com/docs/api/plans/create).

This recipe includes [5 server implementations](server/README.md) in our most popular languages. 

If you want to run the recipe locally, copy the .env.example file to your own .env file in this directory: 

```
cp .env.example .env
```

You will need to update the values in .env to include your API keys, the ID of the Plan, and your website's domain so Checkout can redirect the customer back.


## FAQ
Q: Why did you pick these frameworks?

A: We chose the most minimal framework to convey the key Stripe calls and concepts you need to understand. These demos are meant as an educational tool that helps you roadmap how to integrate Stripe within your own system independent of the framework.

Q: Can you show me how to build X?

A: We are always looking for new recipe ideas, please email dev-samples@stripe.com with your suggestion!

## Author(s)
[@adreyfus-stripe](https://twitter.com/adrind)
