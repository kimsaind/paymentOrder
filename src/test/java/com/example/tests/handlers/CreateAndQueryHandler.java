package com.example.tests.handlers;

import com.example.api.PaymentOrderAPI;
import com.example.api.TokenAPI;
import com.example.api.TransactionTraceAPI;
import com.example.tests.utils.TestUtils;
import com.example.utils.LoggerUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Component("CREATE_AND_QUERY")
public class CreateAndQueryHandler implements ApiTestHandler {
    private static final long DEFAULT_WAIT_TIME_SECONDS = 20; // Tăng mặc định lên 20 giây
    private static final long MIN_WAIT_TIME_SECONDS = 5;
    private static final long MAX_WAIT_TIME_SECONDS = 60;

    private final String token;
    private final ObjectMapper mapper;

    @Autowired
    public CreateAndQueryHandler(ObjectMapper mapper) {
        this.token = TokenAPI.getAccessToken();
        LoggerUtil.info("Access token in CreateAndQueryHandler: {}", token);
        this.mapper = mapper;
    }

    @Override
    @Step("Execute CREATE_AND_QUERY test case")
    public Response execute(Map<String, Object> testCase, Map<String, String> dynamicValues) throws Exception {
        String requestTemplateKey = (String) testCase.get("requestTemplateKey");
        if (requestTemplateKey == null) {
            throw new IllegalArgumentException("requestTemplateKey cannot be null in test case");
        }

        ObjectNode dynamicRequest = TestUtils.createDynamicRequest(requestTemplateKey, dynamicValues);
        if (dynamicRequest == null) {
            throw new IllegalArgumentException("dynamicRequest cannot be null after createDynamicRequest");
        }

        String traceNumber = TestUtils.generateTraceNumber(dynamicRequest, dynamicValues);
        LoggerUtil.info("Generated traceNumber: {}", traceNumber);
        TestUtils.updateRequestBody(dynamicRequest);

        Allure.addAttachment("Dynamic transactionTraceNumber", traceNumber);
        Response createResponse = TestUtils.callApiAndLogResponse(
                "Step 1: Sending create payment order request",
                dynamicRequest.toPrettyString(),
                PaymentOrderAPI.createPaymentOrder(token, dynamicRequest)
        );

        // Kiểm tra response từ bước create
        createResponse.then().statusCode(200);
        LoggerUtil.info("Create Response Body: \n{}", createResponse.getBody().asPrettyString());
        Allure.addAttachment("Create Response Body", "application/json", createResponse.getBody().asPrettyString());

        String status = createResponse.jsonPath().getString("responseData.transaction.status");
        if (status == null) {
            throw new RuntimeException("status not found in create payment order response");
        }
        assertThat("Payment order status should be ORIG", status, equalTo("ORIG"));

        String createdTraceNumber = createResponse.jsonPath().getString("responseData.transaction.transactionTraceNumber");
        if (createdTraceNumber == null) {
            throw new RuntimeException("transactionTraceNumber not found in create payment order response");
        }
        LoggerUtil.info("Received transactionTraceNumber from create response: {}", createdTraceNumber);
        Allure.addAttachment("Created transactionTraceNumber", createdTraceNumber);

        // Lấy thời gian chờ từ test case
        Long waitTimeSeconds = testCase.get("waitTime") != null ? ((Number) testCase.get("waitTime")).longValue() : DEFAULT_WAIT_TIME_SECONDS;

        // Kiểm tra giá trị waitTime hợp lệ
        if (waitTimeSeconds < MIN_WAIT_TIME_SECONDS) {
            LoggerUtil.warn("waitTime {} seconds is below minimum ({} seconds), using minimum instead", waitTimeSeconds, MIN_WAIT_TIME_SECONDS);
            waitTimeSeconds = MIN_WAIT_TIME_SECONDS;
        } else if (waitTimeSeconds > MAX_WAIT_TIME_SECONDS) {
            LoggerUtil.warn("waitTime {} seconds exceeds maximum ({} seconds), using maximum instead", waitTimeSeconds, MAX_WAIT_TIME_SECONDS);
            waitTimeSeconds = MAX_WAIT_TIME_SECONDS;
        }

        long waitTimeMillis = waitTimeSeconds * 1000;
        LoggerUtil.info("Waiting {} seconds ({} ms) before querying transaction history", waitTimeSeconds, waitTimeMillis);
        Allure.step("Waiting " + waitTimeSeconds + " seconds before querying transaction history");
        Thread.sleep(waitTimeMillis);

        String fromDate = TestUtils.getDefaultFromDate();
        String toDate = TestUtils.getDefaultToDate();
        dynamicValues.put("transactionTraceNumber", createdTraceNumber);
        testCase.put("transactionTraceNumber", createdTraceNumber); // Lưu vào testCase để sử dụng trong validateResponse
        Allure.addAttachment("Query transactionTraceNumber", createdTraceNumber);
        Allure.addAttachment("Query fromDate", fromDate);
        Allure.addAttachment("Query toDate", toDate);

        LoggerUtil.info("Sending Transaction History Request - transactionTraceNumber: {}, fromDate: {}, toDate: {}",
                createdTraceNumber, fromDate, toDate);
        LoggerUtil.info("Transaction History Request Body: None (GET request)");
        Allure.addAttachment("Transaction History Request - Query Params",
                "transactionTraceNumber: " + createdTraceNumber + "\nfromDate: " + fromDate + "\ntoDate: " + toDate);
        Allure.addAttachment("Transaction History Request Body", "None (GET request)");

        Response response = TransactionTraceAPI.getTransactionHistory(token, createdTraceNumber, fromDate, toDate);
        return TestUtils.callApiAndLogResponse(
                "Step 2: Querying transaction history with created trace",
                "None (GET request)",
                response
        );
    }

    @Override
    @Step("Validate response for CREATE_AND_QUERY test case")
    public void validateResponse(Response response, Map<String, Object> testCase) {
        response.then().statusCode((Integer) testCase.get("expectedStatusCode"));

        LoggerUtil.info("Query Response Body: \n{}", response.getBody().asPrettyString());
        Allure.addAttachment("Query Response Body", "application/json", response.getBody().asPrettyString());

        String transactionTraceNumber = (String) testCase.get("transactionTraceNumber");
        if (transactionTraceNumber == null) {
            throw new IllegalStateException("transactionTraceNumber not found in test case during validation");
        }

        List<Map<String, Object>> transactions = response.jsonPath().getList("responseData.transactions");
        if (transactions == null || transactions.isEmpty()) {
            throw new RuntimeException("No transactions found in history for traceNumber: " + transactionTraceNumber);
        }

        String queryStatus = response.jsonPath().getString("responseData.transactions[0].status");
        if (queryStatus == null) {
            throw new RuntimeException("status not found in query transaction history response");
        }
        assertThat("Transaction history status should be TRAN", queryStatus, equalTo("TRAN"));
    }
}