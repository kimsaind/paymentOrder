package com.example.tests.handlers;

import com.example.api.TokenAPI;
import com.example.api.TransactionTraceAPI;
import com.example.tests.utils.TestUtils;
import com.example.utils.LoggerUtil;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("GET_TRANSACTION")
public class GetTransactionHandler implements ApiTestHandler {
    private final String token;

    public GetTransactionHandler() {
        this.token = TokenAPI.getAccessToken();
        LoggerUtil.info("Access token in GetTransactionHandler: {}", token);
    }

    @Override
    @Step("Execute GET_TRANSACTION test case")
    public Response execute(Map<String, Object> testCase, Map<String, String> dynamicValues) throws Exception {
        String transactionTraceNumber = (String) testCase.get("transactionTraceNumber");
        String fromDate = TestUtils.getDefaultFromDate();
        String toDate = TestUtils.getDefaultToDate();

        dynamicValues.put("transactionTraceNumber", transactionTraceNumber);
        Allure.addAttachment("Dynamic transactionTraceNumber", transactionTraceNumber);
        Allure.addAttachment("Dynamic fromDate", fromDate);
        Allure.addAttachment("Dynamic toDate", toDate);

        LoggerUtil.info("Sending Transaction History Request - transactionTraceNumber: {}, fromDate: {}, toDate: {}",
                transactionTraceNumber, fromDate, toDate);
        LoggerUtil.info("Transaction History Request Body: None (GET request)");
        Allure.addAttachment("Transaction History Request - Query Params",
                "transactionTraceNumber: " + transactionTraceNumber + "\nfromDate: " + fromDate + "\ntoDate: " + toDate);
        Allure.addAttachment("Transaction History Request Body", "None (GET request)");

        Response response = TransactionTraceAPI.getTransactionHistory(token, transactionTraceNumber, fromDate, toDate);
        return TestUtils.callApiAndLogResponse(
                "Querying transaction history",
                "None (GET request)",
                response
        );
    }

    @Override
    @Step("Validate response for GET_TRANSACTION test case")
    public void validateResponse(Response response, Map<String, Object> testCase) {
        response.then().statusCode((Integer) testCase.get("expectedStatusCode"));
    }
}