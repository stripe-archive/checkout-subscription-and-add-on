package main

import (
	"bytes"
	"encoding/json"
	"io"
	"io/ioutil"
	"log"
	"net/http"
	"os"

	"github.com/joho/godotenv"
	"github.com/stripe/stripe-go/v71"
	"github.com/stripe/stripe-go/v71/checkout/session"
	"github.com/stripe/stripe-go/v71/customer"
	"github.com/stripe/stripe-go/v71/webhook"
)

func main() {
	if err := godotenv.Load(); err != nil {
		log.Fatalf("godotenv.Load: %v", err)
	}

	stripe.Key = os.Getenv("STRIPE_SECRET_KEY")

	http.Handle("/", http.FileServer(http.Dir(os.Getenv("STATIC_DIR"))))
	http.HandleFunc("/checkout-session", handleCheckoutSession)
	http.HandleFunc("/create-checkout-session", handleCreateCheckoutSession)
	http.HandleFunc("/publishable-key", handlePublishableKey)
	http.HandleFunc("/webhook", handleWebhook)

	addr := "localhost:4242"
	log.Printf("Listening on %s ...", addr)
	log.Fatal(http.ListenAndServe(addr, nil))
}

func handleCreateCheckoutSession(w http.ResponseWriter, r *http.Request) {
	if r.Method != "POST" {
		http.Error(w, http.StatusText(http.StatusMethodNotAllowed), http.StatusMethodNotAllowed)
		return
	}
	var req struct {
		Donation int64 `json:"donation"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		log.Printf("json.NewDecoder.Decode: %v", err)
		return
	}

	lineItems := []*stripe.CheckoutSessionLineItemParams{
		&stripe.CheckoutSessionLineItemParams{
			Price:    stripe.String(os.Getenv("SUBSCRIPTION_PRICE_ID")),
			Quantity: stripe.Int64(1),
		},
	}

	if req.Donation > 0 {
		lineItems = append(lineItems, &stripe.CheckoutSessionLineItemParams{
			PriceData: &stripe.CheckoutSessionLineItemPriceDataParams{
				Product:    stripe.String(os.Getenv("DONATION_PRODUCT_ID")),
				UnitAmount: stripe.Int64(req.Donation),
				Currency:   stripe.String(string(stripe.CurrencyUSD)),
			},
			Quantity: stripe.Int64(1),
		})
	}

	params := &stripe.CheckoutSessionParams{
		Mode: stripe.String("subscription"),
		PaymentMethodTypes: stripe.StringSlice([]string{
			"card",
		}),
		SuccessURL: stripe.String(os.Getenv("DOMAIN") + "/success.html?session_id={CHECKOUT_SESSION_ID}"),
		CancelURL:  stripe.String(os.Getenv("DOMAIN") + "/cancel.html"),
		LineItems:  lineItems,
	}

	session, err := session.New(params)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		log.Printf("session.New: %v", err)
		return
	}
	writeJSON(w, struct {
		CheckoutSessionID string `json:"checkoutSessionId"`
	}{
		CheckoutSessionID: session.ID,
	})
}

func handlePublishableKey(w http.ResponseWriter, r *http.Request) {
	if r.Method != "GET" {
		http.Error(w, http.StatusText(http.StatusMethodNotAllowed), http.StatusMethodNotAllowed)
		return
	}
	writeJSON(w, struct {
		PublishableKey string `json:"publishableKey"`
	}{
		PublishableKey: os.Getenv("STRIPE_PUBLISHABLE_KEY"),
	})
}

func handleCheckoutSession(w http.ResponseWriter, r *http.Request) {
	if r.Method != "GET" {
		http.Error(w, http.StatusText(http.StatusMethodNotAllowed), http.StatusMethodNotAllowed)
		return
	}

	id := r.FormValue("sessionId")

	if id == "" {
		log.Println("CheckoutSession ID is missing from URL", r.URL.Query())
		http.Error(w, http.StatusText(http.StatusBadRequest), http.StatusBadRequest)
		return
	}

	// Fetch the CheckoutSession object from your success page
	// to get details about the order
	session, err := session.Get(id, nil)

	if err != nil {
		log.Printf("An error happened when getting the CheckoutSession %q from Stripe: %v", id, err)
		http.Error(w, http.StatusText(http.StatusBadRequest), http.StatusBadRequest)
		return
	}

	writeJSON(w, struct {
		CheckoutSession *stripe.CheckoutSession `json:"checkoutSession"`
	}{
		CheckoutSession: session,
	})
}

func handleWebhook(w http.ResponseWriter, r *http.Request) {
	if r.Method != "POST" {
		http.Error(w, http.StatusText(http.StatusMethodNotAllowed), http.StatusMethodNotAllowed)
		return
	}
	b, err := ioutil.ReadAll(r.Body)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		log.Printf("ioutil.ReadAll: %v", err)
		return
	}

	event, err := webhook.ConstructEvent(b, r.Header.Get("Stripe-Signature"), os.Getenv("STRIPE_WEBHOOK_SECRET"))
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		log.Printf("webhook.ConstructEvent: %v", err)
		return
	}

	if event.Type != "checkout.session.completed" {
		return
	}

	cust, err := customer.Get(event.GetObjectValue("customer"), nil)
	if err != nil {
		log.Printf("customer.Get: %v", err)
		return
	}

	if event.GetObjectValue("display_items", "0", "custom") != "" &&
		event.GetObjectValue("display_items", "0", "custom", "name") == "Pasha e-book" {
		log.Printf("🔔 Customer is subscribed and bought an e-book! Send the e-book to %s", cust.Email)
	} else {
		log.Printf("🔔 Customer is subscribed but did not buy an e-book.")
	}
}

func writeJSON(w http.ResponseWriter, v interface{}) {
	var buf bytes.Buffer
	if err := json.NewEncoder(&buf).Encode(v); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		log.Printf("json.NewEncoder.Encode: %v", err)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	if _, err := io.Copy(w, &buf); err != nil {
		log.Printf("io.Copy: %v", err)
		return
	}
}
