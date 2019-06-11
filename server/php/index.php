<?php
use Slim\Http\Request;
use Slim\Http\Response;
use Stripe\Stripe;

require 'vendor/autoload.php';
require './config.php';

$dotenv = Dotenv\Dotenv::create(realpath('../..'));
$dotenv->load();

$app = new \Slim\App;

// Instantiate the logger as a dependency
$container = $app->getContainer();
$container['logger'] = function ($c) {
  $settings = $c->get('settings')['logger'];
  $logger = new Monolog\Logger($settings['name']);
  $logger->pushProcessor(new Monolog\Processor\UidProcessor());
  $logger->pushHandler(new Monolog\Handler\StreamHandler(__DIR__ . '/logs/app.log', \Monolog\Logger::DEBUG));
  return $logger;
};

$app->add(function ($request, $response, $next) {
    Stripe::setApiKey(getenv('STRIPE_SECRET_KEY'));
    return $next($request, $response);
});
  

$app->get('/', function (Request $request, Response $response, array $args) {   
  // Display checkout page
  return $response->write(file_get_contents('../../client/index.html'));
});

$app->get('/public-key', function (Request $request, Response $response, array $args) {
    $pub_key = getenv('STRIPE_PUBLIC_KEY');
    return $response->withJson([ 'publicKey' => $pub_key ]);
});

$app->post('/create-checkout-session', function(Request $request, Response $response, array $args) {
  $logger = $this->get('logger');

  $domain_url = getenv('DOMAIN');
  $plan_id = getEnv('SUBSCRIPTION_PLAN_ID');
  $body = json_decode($request->getBody());
  $logger->info('🔔  ' . $body->isBuyingSticker);

  if($body->isBuyingSticker) {
    // Customer is signing up for a subscription and purchasing the extra e-book
    $checkout_session = \Stripe\Checkout\Session::create([
      'success_url' => $domain_url . '/success.html',
      'cancel_url' => $domain_url . '/cancel.html',
      'payment_method_types' => ['card'],
      'subscription_data' => [
        'items' => [[
          'plan' => $plan_id,
        ]],
      ],
      'line_items' => [[
        'name' => 'Pasha e-book',
        'amount' => 300,
        'currency' => 'usd',
        'quantity' => 1
      ]]
    ]);
  } else {
    // Customer is only signing up for a subscription
    $checkout_session = \Stripe\Checkout\Session::create([
      'success_url' => $domain_url . '/success.html',
      'cancel_url' => $domain_url . '/cancel.html',
      'payment_method_types' => ['card'],
      'subscription_data' => [
        'items' => [[
          'plan' => $plan_id,
        ]],
      ],
    ]);
  }

  return $response->withJson(array('checkoutSessionId' => $checkout_session['id']));

});

$app->post('/webhook', function(Request $request, Response $response) {
    $logger = $this->get('logger');
    $event = $request->getParsedBody();
    // Parse the message body (and check the signature if possible)
    $webhookSecret = getenv('STRIPE_WEBHOOK_SECRET');
    if ($webhookSecret) {
      try {
        $event = \Stripe\Webhook::constructEvent(
          $request->getBody(),
          $request->getHeaderLine('stripe-signature'),
          $webhookSecret
        );
      } catch (\Exception $e) {
        return $response->withJson([ 'error' => $e->getMessage() ])->withStatus(403);
      }
    } else {
      $event = $request->getParsedBody();
    }
    $type = $event['type'];
    $object = $event['data']['object'];

    if($type == 'checkout.session.completed') {
      $items = $object['display_items'];
      $customer = \Stripe\Customer::retrieve($object['customer']);

      if(count($items) > 0 && $items[0]['amount'] == 300) {
        $logger->info('🔔  Customer is subscribed and bought an e-book! Send the e-book to ' . $customer['email']);
      } else {
        $logger->info('🔔  Customer is subscribed but did not buy an e-book.');
      }
    } else {
      $logger->info('🔔  Webhook received! ' . $type);
    }
  
    return $response->withJson([ 'status' => 'success' ])->withStatus(200);
});

$app->run();
