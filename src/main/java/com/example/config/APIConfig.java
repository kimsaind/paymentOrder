package com.example.config;

public class APIConfig {
    public static final String BASE_URL = "https://sandbox.acb.com.vn/acb/open";
    public static final String TOKEN_ENDPOINT = BASE_URL + "/iam/id/v1/auth/realms/soba/protocol/openid-connect/token";
    public static final String PAYMENT_ENDPOINT = BASE_URL + "/payments/payment-order/v2/transaction";
    public static final String CLIENT_ID = "2f4e1379addb2c6f059d53f622b6bf80";
    public static final String CLIENT_SECRET = "4b6d95fa2f6fb3d00be14834d6e685e0";
}