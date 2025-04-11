package com.example.api;

import com.example.config.APIConfig;
import com.example.utils.LoggerUtil;
import io.qameta.allure.Allure;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

public class BulkTransactionQueryAPI {
    // Giữ nguyên endpoint vì nó khớp với URL bạn cung cấp
    private static final String BULK_TRANSACTION_HISTORY_ENDPOINT = APIConfig.BASE_URL + "/payments/payment-order/v2/bulk-transaction";

    public static Response getBulkTransaction(String token, String bulkTraceNumber, String fromDate, String toDate) {
        RequestSpecification request = RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .header("X-Channel", "SOBA")
                .header("X-Client-ID", APIConfig.CLIENT_ID)
                .header("X-Provider-ID", "BULK-TRANSACTION")
                .header("X-Request-ID", java.util.UUID.randomUUID().toString())
                .queryParam("bulkTraceNumber", bulkTraceNumber)
                .queryParam("fromDate", fromDate)
                .queryParam("toDate", toDate);
//           // Thêm các query parameters bổ sung (giả định API yêu cầu)
////                .queryParam("channel", "SOBA") // Thêm channel nếu API yêu cầu
//                .queryParam("providerId", "BULK-TRANSACTION"); // Thêm providerId nếu API yêu cầu

        // Log request
        LoggerUtil.info("Sending Bulk Transaction History Request to: {}", BULK_TRANSACTION_HISTORY_ENDPOINT);
        LoggerUtil.info("Request Headers: {}", request.log().headers().toString());
        LoggerUtil.info("Request Query Params: bulkTraceNumber={}, fromDate={}, toDate={}, channel={}, providerId={}",
                bulkTraceNumber, fromDate, toDate, "SOBA", "BULK-TRANSACTION");
        Allure.addAttachment("Bulk Transaction History Request - URL", BULK_TRANSACTION_HISTORY_ENDPOINT);
        Allure.addAttachment("Bulk Transaction History Request - Headers", request.log().headers().toString());
        Allure.addAttachment("Bulk Transaction History Request - Query Params",
                "bulkTraceNumber: " + bulkTraceNumber + "\nfromDate: " + fromDate + "\ntoDate: " + toDate +
                        "\nchannel: SOBA" + "\nproviderId: BULK-TRANSACTION");

        // Gửi yêu cầu
        Response response = request.get(BULK_TRANSACTION_HISTORY_ENDPOINT);

        // Log response
        LoggerUtil.info("Bulk Transaction History Response - Status Code: {}", response.getStatusCode());
        LoggerUtil.info("Bulk Transaction History Response - Headers: {}", response.getHeaders());
        LoggerUtil.info("Bulk Transaction History Response - Body: \n{}", response.getBody().asPrettyString());
        Allure.addAttachment("Bulk Transaction History Response - Status Code", String.valueOf(response.getStatusCode()));
        Allure.addAttachment("Bulk Transaction History Response - Headers", response.getHeaders().toString());
        Allure.addAttachment("Bulk Transaction History Response - Body", "application/json", response.getBody().asPrettyString());

        return response;
    }
}