package com.example.api;

import com.example.utils.LoggerUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Allure;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

public class PaymentOrderAPI {
    private static final String BASE_URL = "https://sandbox.acb.com.vn/acb/open/payments/payment-order/v2/transaction";
    private static final ObjectMapper mapper = new ObjectMapper();



    public static Response createPaymentOrder(String token, Object requestBody) throws Exception {
        RequestSpecification request = RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .header("X-Channel", "SOBA")
                .header("X-Client-ID", "2f4e1379addb2c6f059d53f622b6bf80")
                .header("X-Provider-ID", "PIKA")
                .header("X-Request-ID", java.util.UUID.randomUUID().toString())
                .contentType("application/json")
                .body(mapper.writeValueAsString(requestBody));

        // Log request
        LoggerUtil.info("Sending Payment Order Request to: {}", BASE_URL);
        LoggerUtil.info("Request Headers: {}", request.log().headers().toString());
        LoggerUtil.info("Request Body: \n{}", mapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestBody));
        Allure.addAttachment("Payment Order Request - URL", BASE_URL);
        Allure.addAttachment("Payment Order Request - Headers", request.log().headers().toString());
        Allure.addAttachment("Payment Order Request - Body", "application/json", mapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestBody));

        // Gửi yêu cầu
        Response response = request.post(BASE_URL);

        // Log response
        LoggerUtil.info("Payment Order Response - Status Code: {}", response.getStatusCode());
        LoggerUtil.info("Payment Order Response - Headers: {}", response.getHeaders());
        LoggerUtil.info("Payment Order Response - Body: \n{}", response.getBody().asPrettyString());
        Allure.addAttachment("Payment Order Response - Status Code", String.valueOf(response.getStatusCode()));
        Allure.addAttachment("Payment Order Response - Headers", response.getHeaders().toString());
        Allure.addAttachment("Payment Order Response - Body", "application/json", response.getBody().asPrettyString());

        return response;
    }
}