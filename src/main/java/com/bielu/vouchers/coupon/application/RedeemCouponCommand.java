package com.bielu.vouchers.coupon.application;

import java.net.InetAddress;

/**
 * Raw input for a redemption. {@code clientAddress} comes from the connection, never from a client-supplied header.
 */
public record RedeemCouponCommand(String code, String userId, InetAddress clientAddress) {
}
