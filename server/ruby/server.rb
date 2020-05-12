# frozen_string_literal: true

require 'stripe'
require 'sinatra'
require 'dotenv'

# Copy the .env.example in the root into a .env file in this folder
Dotenv.load
Stripe.api_key = ENV['STRIPE_SECRET_KEY']

set :static, true
set :public_folder, File.join(File.dirname(__FILE__), ENV['STATIC_DIR'])
set :port, 4242

get '/' do
  content_type 'text/html'
  send_file File.join(settings.public_folder, 'index.html')
end

get '/publishable-key' do
  content_type 'application/json'
  {
    publishableKey: ENV['STRIPE_PUBLISHABLE_KEY']
  }.to_json
end

def create_checkout_session(donation, price_id, product_id, domain_url)
  line_items = [{
    price: price_id,
    quantity: 1
  }]

  if donation > 0
    line_items.append({price_data: {product: product_id, unit_amount: donation, currency: 'usd'}, quantity: 1})
  end

  checkout_session = Stripe::Checkout::Session.create(
    mode: 'subscription',
    success_url: domain_url + '/success.html?session_id={CHECKOUT_SESSION_ID}',
    cancel_url: domain_url + '/cancel.html',
    payment_method_types: ['card'],
    line_items: line_items
  )

  checkout_session
end

post '/create-checkout-session' do
  content_type 'application/json'
  data = JSON.parse request.body.read
  checkout_session = create_checkout_session(data['donation'], ENV['SUBSCRIPTION_PRICE_ID'], ENV['DONATION_PRODUCT_ID'], ENV['DOMAIN'])

  {
    checkoutSessionId: checkout_session['id']
  }.to_json
end

# Fetch the Checkout Session to display the JSON result on the success page
get '/checkout-session' do
  content_type 'application/json'
  session_id = params[:sessionId]

  session = Stripe::Checkout::Session.retrieve(session_id)
  session.to_json
end

post '/webhook' do
  # You can use webhooks to receive information about asynchronous payment events.
  # For more about our webhook events check out https://stripe.com/docs/webhooks.
  webhook_secret = ENV['STRIPE_WEBHOOK_SECRET']
  payload = request.body.read
  if !webhook_secret.empty?
    # Retrieve the event by verifying the signature using the raw body and secret if webhook signing is configured.
    sig_header = request.env['HTTP_STRIPE_SIGNATURE']
    event = nil

    begin
      event = Stripe::Webhook.construct_event(
        payload, sig_header, webhook_secret
      )
    rescue JSON::ParserError => e
      # Invalid payload
      status 400
      return
    rescue Stripe::SignatureVerificationError => e
      # Invalid signature
      puts '⚠️  Webhook signature verification failed.'
      status 400
      return
    end
  else
    data = JSON.parse(payload, symbolize_names: true)
    event = Stripe::Event.construct_from(data)
  end
  # Get the type of webhook event sent - used to check the status of PaymentIntents.
  event_type = event['type']
  data = event['data']
  data_object = data['object']

  if event_type == 'checkout.session.completed'
    items = data_object['display_items']
    customer = Stripe::Customer.retrieve(data_object['customer'])
    if items.length && items[0]['custom'] && items[0]['custom']['name']
      puts '🔔  Customer is subscribed and bought an e-book! Send the e-book to ' + customer['email']
    else
      puts '🔔  Customer is subscribed but did not buy e-book'
    end
  end

  puts '🔔  Webhook received!' if event_type == 'some.event'

  content_type 'application/json'
  {
    status: 'success'
  }.to_json
end
