<?php

require_once 'shared.php';

$domain_url = $config['domain'];
$plan_id = $config['subscription_plan_id'];

if($body->isBuyingSticker) {
  // Customer is signing up for a subscription and purchasing the extra e-book
  $checkout_session = \Stripe\Checkout\Session::create([
    'success_url' => $domain_url . '/success.html?session_id={CHECKOUT_SESSION_ID}',
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
    'success_url' => $domain_url . '/success.html?session_id={CHECKOUT_SESSION_ID}',
	'cancel_url' => $domain_url . '/cancel.html',
	'payment_method_types' => ['card'],
	'subscription_data' => [
	  'items' => [[
		'plan' => $plan_id,
	  ]],
	],
  ]);
}

echo json_encode(['checkoutSessionId' => $checkout_session['id']]);
