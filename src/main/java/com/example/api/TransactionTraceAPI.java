package com.example.api;

import com.example.config.APIConfig;
import com.example.utils.LoggerUtil;
import io.qameta.allure.Allure;
import io.restassured.response.Response;

import java.util.UUID;

import static io.restassured.RestAssured.given;


public class TransactionTraceAPI {
    public static Response getTransactionHistory(String token, String transactionTraceNumber, String fromDate, String toDate) {
        String endpoint = APIConfig.PAYMENT_ENDPOINT + "?transactionTraceNumber=" + transactionTraceNumber +
                "&fromDate=" + fromDate + "&toDate=" + toDate;
        LoggerUtil.info("Sending Transaction History Request to: {}", endpoint);

        // Sinh UUID động cho X-Request-ID
        String requestId = UUID.randomUUID().toString();

        String headersLog = String.format(
                "X-Channel=SOBA, X-Client-ID=%s, X-Provider-ID=PAYMENT-ORDER, X-Request-ID=%s, Authorization=Bearer %s",
                APIConfig.CLIENT_ID, requestId, token
        );
        Allure.addAttachment("Request Headers", headersLog);
        LoggerUtil.info("Request Headers: {}", headersLog);

        Response response = given()
                .header("X-Channel", "SOBA")
                .header("X-Client-ID", APIConfig.CLIENT_ID)
                .header("X-Provider-ID", "PIKA")
                .header("X-Request-ID", requestId)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .get(endpoint);

        Allure.addAttachment("Response Status Code", String.valueOf(response.getStatusCode()));
        Allure.addAttachment("Response Headers", response.getHeaders().toString());
        LoggerUtil.info("Response Status Code: {}", response.getStatusCode());
        LoggerUtil.info("Response Headers: {}", response.getHeaders().toString());

        String responseBody = response.getBody().asString();
        Allure.addAttachment("Response Body", "application/json", responseBody);
        LoggerUtil.info("Response Body: {}", responseBody);

        return response;
    }
}