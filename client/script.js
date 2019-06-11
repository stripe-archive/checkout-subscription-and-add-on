var stripe;
var checkoutSessionId;

var setupElements = function() {
  fetch("/public-key", {
    method: "GET",
    headers: {
      "Content-Type": "application/json"
    }
  })
    .then(function(result) {
      return result.json();
    })
    .then(function(data) {
      stripe = Stripe(data.publicKey);
    });
};

var createCheckoutSession = function(isBuyingSticker) {
  fetch("/create-checkout-session", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ isBuyingSticker })
  })
    .then(function(result) {
      return result.json();
    })
    .then(function(data) {
      checkoutSessionId = data.checkoutSessionId;
    });
};
setupElements();
createCheckoutSession(false);

document
  .querySelector('input[name="subscribe"]')
  .addEventListener("change", function(evt) {
    if (this.checked) {
      createCheckoutSession(true);
      document.querySelector(".order-total").textContent = "$17.00"; // Because they are buying the extra item
    } else {
      createCheckoutSession(false);
      document.querySelector(".order-total").textContent = "$14.00"; // Not buying the extra item
    }
  });

document.querySelector("#submit").addEventListener("click", function(evt) {
  evt.preventDefault();
  // Initiate payment
  stripe
    .redirectToCheckout({
      sessionId: checkoutSessionId
    })
    .then(function(result) {
      console.log("error");
      // If `redirectToCheckout` fails due to a browser or network
      // error, display the localized error message to your customer
      // using `result.error.message`.
    })
    .catch(function(err) {
      console.log(err);
    });
});
