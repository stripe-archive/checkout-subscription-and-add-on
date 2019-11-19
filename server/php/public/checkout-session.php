<?php

require_once 'shared.php';

$id = $_GET["sessionId"];

$checkout_session = \Stripe\Checkout\Session::retrieve($id);

echo json_encode($checkout_session);
