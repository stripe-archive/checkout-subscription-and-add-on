<?php

require_once 'shared.php';


$event = null;

try {
	// Make sure the event is coming from Stripe by checking the signature header
	$event = \Stripe\Webhook::constructEvent($input, $_SERVER['HTTP_STRIPE_SIGNATURE'], $config['stripe_webhook_secret']);
}
catch (Exception $e) {
	http_response_code(403);
	echo json_encode([ 'error' => $e->getMessage() ]);
	exit;
}

$details = '';

$type = $event['type'];
$object = $event['data']['object'];

if($type == 'checkout.session.completed') {
  $items = $object['display_items'];
  $customer = \Stripe\Customer::retrieve($object['customer']);

  if(count($items) > 0 && $items[0]['amount'] == 300) {
	error_log('🔔  Customer is subscribed and bought an e-book! Send the e-book to ' . $customer['email']);
  } else {
	error_log('🔔  Customer is subscribed but did not buy an e-book.');
  }
} else {
  error_log('🔔  Webhook received! ' . $type);
}

$output = [
	'status' => 'success'
];

echo json_encode($output, JSON_PRETTY_PRINT);
