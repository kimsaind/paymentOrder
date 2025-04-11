package com.example.tests.handlers;

import com.example.api.BulkTransactionQueryAPI;
import com.example.api.TokenAPI;
import com.example.tests.utils.TestUtils;
import com.example.utils.LoggerUtil;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("GET_BULK_TRANSACTION")
public class GetBulkTransactionHandler implements ApiTestHandler {
    private final String token;

    public GetBulkTransactionHandler() {
        this.token = TokenAPI.getAccessToken();
        LoggerUtil.info("Access token in GetBulkTransactionHandler: {}", token);
    }

    @Override
    @Step("Execute GET_BULK_TRANSACTION test case")
    public Response execute(Map<String, Object> testCase, Map<String, String> dynamicValues) throws Exception {
        String bulkTraceNumber = (String) testCase.get("bulkTraceNumber");
        String fromDate = (String) testCase.get("fromDate");
        String toDate = (String) testCase.get("toDate");

        if (fromDate == null || fromDate.trim().isEmpty()) {
            fromDate = "2025-04-10";
            LoggerUtil.info("fromDate not specified in test case, using default: {}", fromDate);
        }
        if (toDate == null || toDate.trim().isEmpty()) {
            toDate = "2025-04-10";
            LoggerUtil.info("toDate not specified in test case, using default: {}", toDate);
        }

        dynamicValues.put("bulkTraceNumber", bulkTraceNumber);
        Allure.addAttachment("Dynamic bulkTraceNumber", bulkTraceNumber);
        Allure.addAttachment("Dynamic fromDate", fromDate);
        Allure.addAttachment("Dynamic toDate", toDate);

        LoggerUtil.info("Sending Bulk Transaction History Request - bulkTraceNumber: {}, fromDate: {}, toDate: {}",
                bulkTraceNumber, fromDate, toDate);
        LoggerUtil.info("Bulk Transaction History Request Body: None (GET request)");
        Allure.addAttachment("Bulk Transaction History Request - Query Params",
                "bulkTraceNumber: " + bulkTraceNumber + "\nfromDate: " + fromDate + "\ntoDate: " + toDate);
        Allure.addAttachment("Bulk Transaction History Request Body", "None (GET request)");

        Response response = BulkTransactionQueryAPI.getBulkTransaction(token, bulkTraceNumber, fromDate, toDate);
        return TestUtils.callApiAndLogResponse(
                "Querying bulk transaction",
                "None (GET request)",
                response
        );
    }

    @Override
    @Step("Validate response for GET_BULK_TRANSACTION test case")
    public void validateResponse(Response response, Map<String, Object> testCase) {
        response.then().statusCode((Integer) testCase.get("expectedStatusCode"));
    }
}