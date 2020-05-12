<?php

require_once 'shared.php';

$domain_url = $config['domain'];
$price_id = $config['subscription_price_id'];
$product_id = $config['donation_product_id'];

$line_items = [[
	'price' => $price_id,
	'quantity' => 1
]];

if ($body->donation > 0) {
	$line_items[] = ['quantity' => 1, 'price_data' => ['unit_amount' => $body->donation, 'currency' => 'usd', 'product' => $product_id]];
}

// Sign customer up for subscription
$checkout_session = \Stripe\Checkout\Session::create([
	'success_url' => $domain_url . '/success.html?session_id={CHECKOUT_SESSION_ID}',
	'cancel_url' => $domain_url . '/cancel.html',
	'payment_method_types' => ['card'],
	'mode' => 'subscription',
	'line_items' => $line_items
]);

echo json_encode(['checkoutSessionId' => $checkout_session['id']]);
