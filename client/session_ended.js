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
        checkout = stripe.retr
      });
  };
  