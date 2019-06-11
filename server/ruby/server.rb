require 'stripe'
require 'sinatra'
require 'dotenv'

Dotenv.load(File.dirname(__FILE__) + '/../../.env')
Stripe.api_key = ENV['STRIPE_SECRET_KEY']

set :static, true
set :public_folder, File.join(File.dirname(__FILE__), '../../client/')
set :port, 4242

get '/' do
  content_type 'text/html'
  send_file File.join(settings.public_folder, 'index.html')
end

get '/public-key' do
  content_type 'application/json'
  {
    publicKey: ENV['STRIPE_PUBLIC_KEY']
  }.to_json
end

def create_checkout_session(is_buying_sticker, plan_id, domain_url)
  checkout_session = if is_buying_sticker
                       # Customer is signing up for a subscription and purchasing the extra e-book
                       Stripe::Checkout::Session.create(
                         success_url: domain_url + '/success.html',
                         cancel_url: domain_url + '/cancel.html',
                         payment_method_types: ['card'],
                         subscription_data: {
                           items: [{ plan: plan_id }]
                         },
                         line_items: [{
                           name: 'Pasha e-book',
                           quantity: 1,
                           currency: 'usd',
                           amount: 300
                         }]
                       )
                     else
                       # Customer is only signing up for a subscription
                       Stripe::Checkout::Session.create(
                         success_url: domain_url + '/success.html',
                         cancel_url: domain_url + '/cancel.html',
                         payment_method_types: ['card'],
                         subscription_data: {
                           items: [{ plan: plan_id }]
                         }
                       )
  end
  checkout_session
end

post '/create-checkout-session' do
  content_type 'application/json'
  data = JSON.parse request.body.read
  checkout_session = create_checkout_session(data['isBuyingSticker'], ENV['SUBSCRIPTION_PLAN_ID'], ENV['DOMAIN'])

  {
    checkoutSessionId: checkout_session['id']
  }.to_json
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
