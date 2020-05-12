var stripe;
var checkoutSessionId;

var setupElements = function () {
  fetch("/publishable-key", {
    method: "GET",
    headers: {
      "Content-Type": "application/json",
    },
  })
    .then(function (result) {
      return result.json();
    })
    .then(function (data) {
      stripe = Stripe(data.publishableKey);
    });
};

var createCheckoutSession = function (donation) {
  return fetch("/create-checkout-session", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ donation: donation * 100 }),
  }).then(function (response) {
    return response.json();
  });
};

setupElements();
createCheckoutSession(false);

document.querySelectorAll(".donation").forEach(function (donationBtn) {
  donationBtn.addEventListener("click", function (evt) {
    if (evt.target.classList.contains("selected")) {
      evt.target.classList.remove("selected");
    } else {
      document.querySelectorAll(".donation").forEach(function (el) {
        el.classList.remove("selected");
      });
      evt.target.classList.add("selected");
    }
  });
});

document.querySelector("#submit").addEventListener("click", function (evt) {
  evt.preventDefault();
  // Initiate payment
  var donation = document.querySelector('.donation.selected');
  var donationAmount = donation ? donation.dataset.amount : 0;
  createCheckoutSession(donationAmount).then(function (response) {
    stripe
      .redirectToCheckout({
        sessionId: response.checkoutSessionId,
      })
      .then(function (result) {
        console.log("error");
        // If `redirectToCheckout` fails due to a browser or network
        // error, display the localized error message to your customer
        // using `result.error.message`.
      })
      .catch(function (err) {
        console.log(err);
      });
  });
});
