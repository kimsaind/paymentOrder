package com.example.api;

import com.example.config.APIConfig;
import com.example.utils.LoggerUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Allure;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

public class BulkTransactionCreateAPI {
    private static final String BULK_TRANSACTION_ENDPOINT = APIConfig.BASE_URL + "/payments/payment-order/v2/bulk-transaction";
    private static final ObjectMapper mapper = new ObjectMapper();

    public static Response createBulkTransaction(String token, Object requestBody) throws Exception {
        RequestSpecification request = RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .header("X-Channel", "SOBA")
                .header("X-Client-ID", APIConfig.CLIENT_ID)
                .header("X-Provider-ID", "PIKA")
                .header("X-Request-ID", java.util.UUID.randomUUID().toString())
                .contentType("application/json")
                .body(mapper.writeValueAsString(requestBody));

        // Log request
        LoggerUtil.info("Sending Bulk Transaction Request to: {}", BULK_TRANSACTION_ENDPOINT);
        LoggerUtil.info("Request Headers: {}", request.log().headers().toString());
        LoggerUtil.info("Request Body: \n{}", mapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestBody));
        Allure.addAttachment("Bulk Transaction Request - URL", BULK_TRANSACTION_ENDPOINT);
        Allure.addAttachment("Bulk Transaction Request - Headers", request.log().headers().toString());
        Allure.addAttachment("Bulk Transaction Request - Body", "application/json", mapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestBody));

        // Gửi yêu cầu
        Response response = request.post(BULK_TRANSACTION_ENDPOINT);

        // Log response
        LoggerUtil.info("Bulk Transaction Response - Status Code: {}", response.getStatusCode());
        LoggerUtil.info("Bulk Transaction Response - Headers: {}", response.getHeaders());
        LoggerUtil.info("Bulk Transaction Response - Body: \n{}", response.getBody().asPrettyString());
        Allure.addAttachment("Bulk Transaction Response - Status Code", String.valueOf(response.getStatusCode()));
        Allure.addAttachment("Bulk Transaction Response - Headers", response.getHeaders().toString());
        Allure.addAttachment("Bulk Transaction Response - Body", "application/json", response.getBody().asPrettyString());

        return response;
    }
}