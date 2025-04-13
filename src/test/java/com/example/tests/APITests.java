package com.example.tests;

import com.example.api.*;
import com.example.tests.handlers.BulkTransactionHandler;
import com.example.tests.handlers.CreateAndQueryBulkHandler;
import com.example.tests.utils.TestUtils;
import com.example.utils.FileUtil;
import com.example.utils.LoggerUtil;
import com.example.utils.SignatureUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.qameta.allure.Allure;
import io.restassured.response.Response;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class APITests {
    private static final Map<String, Response> responseCache = new HashMap<>();
    private static final String token = TokenAPI.getAccessToken();
    private static final ObjectMapper mapper = new ObjectMapper();
    private final CreateAndQueryBulkHandler bulkQueryHandler = new CreateAndQueryBulkHandler(mapper);
    private final BulkTransactionHandler bulkHandler = new BulkTransactionHandler(mapper);

    @TestFactory
    public List<DynamicTest> testAllPaymentOrders() throws Exception {
        List<Map<String, Object>> testCases = FileUtil.getTestCases("all-payment-tests.json");
        String testCaseId = System.getenv("TEST_CASE_ID");
        LoggerUtil.info("TEST_CASE_ID from environment: {}", testCaseId);

        if (testCaseId != null && !testCaseId.isEmpty()) {
            testCases = testCases.stream()
                    .filter(tc -> testCaseId.equals(tc.get("testCaseId")))
                    .collect(Collectors.toList());
            if (testCases.isEmpty()) {
                LoggerUtil.info("No test case found for TEST_CASE_ID: {}", testCaseId);
            } else {
                LoggerUtil.info("Running test case: {}", testCaseId);
            }
        } else {
            LoggerUtil.info("No TEST_CASE_ID specified, running all test cases");
        }

        return testCases.stream().map(tc -> {
            String displayName = (String) tc.get("testName");
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = "Test_" + tc.get("testCaseId");
                LoggerUtil.info("testName is null or blank, using default: {}", displayName);
            }

            return DynamicTest.dynamicTest(displayName, () -> {
                String requestTemplateKey = (String) tc.get("requestTemplateKey");
                Integer expectedStatusCode = (Integer) tc.get("expectedStatusCode");
                String expectedField = (String) tc.get("expectedField");
                String expectedValue = (String) tc.get("expectedValue");
                String apiType = (String) tc.get("apiType");

                // Thêm log để kiểm tra apiType
                LoggerUtil.info("Processing test case {} with apiType: {}", tc.get("testCaseId"), apiType);
                Allure.step("Test Case ID: " + tc.get("testCaseId"));
                Allure.step("Description: " + tc.get("description"));

                Map<String, String> dynamicValues = new HashMap<>();
                Response response;

                if ("CREATE_AND_QUERY".equals(apiType)) {
                    ObjectNode dynamicRequest = TestUtils.createDynamicRequest(requestTemplateKey, dynamicValues);
                    String traceNumber = TestUtils.generateTraceNumber(dynamicRequest, dynamicValues);
                    updateRequestBody(dynamicRequest);

                    Allure.addAttachment("Dynamic requestTrace", dynamicValues.get("requestTrace"));
                    Allure.addAttachment("Dynamic requestDateTime", dynamicValues.get("requestDateTime"));
                    Allure.addAttachment("Dynamic transactionTraceNumber", traceNumber);

                    Allure.step("Step 1: Sending create payment order request");
                    response = TestUtils.callApiAndLogResponse(
                            "Step 1: Sending create payment order request",
                            dynamicRequest.toPrettyString(),
                            PaymentOrderAPI.createPaymentOrder(token, dynamicRequest)
                    );

                    response.then().statusCode(200);

                    String status = response.jsonPath().getString("responseData.transaction.status");
                    assertThat("Payment order status should be ORIG", status, equalTo("ORIG"));

                    String createdTraceNumber = response.jsonPath()
                            .getString("responseData.transaction.transactionTraceNumber");
                    if (createdTraceNumber == null) {
                        throw new RuntimeException("transactionTraceNumber not found in create payment order response");
                    }
                    Allure.addAttachment("Created transactionTraceNumber", createdTraceNumber);

                    Allure.step("Waiting 10 seconds before querying transaction history");
                    Thread.sleep(10000);

                    String fromDate = (String) tc.get("fromDate");
                    String toDate = (String) tc.get("toDate");
                    if (fromDate == null || fromDate.trim().isEmpty()) {
                        fromDate = TestUtils.getDefaultFromDate();
                        LoggerUtil.info("fromDate not specified in test case, using current date: {}", fromDate);
                    }
                    if (toDate == null || toDate.trim().isEmpty()) {
                        toDate = TestUtils.getDefaultToDate();
                        LoggerUtil.info("toDate not specified in test case, using current date: {}", toDate);
                    }

                    dynamicValues.put("transactionTraceNumber", createdTraceNumber);
                    Allure.addAttachment("Query transactionTraceNumber", createdTraceNumber);
                    Allure.addAttachment("Query fromDate", fromDate);
                    Allure.addAttachment("Query toDate", toDate);

                    Allure.step("Step 2: Querying transaction history with created trace");
                    response = TestUtils.callApiAndLogResponse(
                            "Step 2: Querying transaction history with created trace",
                            "None (GET request)",
                            TransactionTraceAPI.getTransactionHistory(token, createdTraceNumber, fromDate, toDate)
                    );

                    response.then().statusCode(200);

                    List<Map<String, Object>> transactions = response.jsonPath().getList("responseData.transactions");
                    if (transactions == null || transactions.isEmpty()) {
                        throw new RuntimeException("No transactions found in history for traceNumber: " + createdTraceNumber);
                    }

                    String queryStatus = response.jsonPath().getString("responseData.transactions[0].status");
                    assertThat("Transaction history status should be TRAN", queryStatus, equalTo("TRAN"));
                } else if ("CREATE_AND_QUERY_BULK".equals(apiType)) {
                    LoggerUtil.info("Executing CREATE_AND_QUERY_BULK for test case: {}", tc.get("testCaseId"));
                    response = bulkQueryHandler.execute(tc, dynamicValues);
                    bulkQueryHandler.validateResponse(response, tc);
                    return;
                } else if ("BULK_TRANSACTION".equals(apiType)) {
                    response = bulkHandler.execute(tc, dynamicValues);
                    bulkHandler.validateResponse(response, tc);
                    return;
                } else if ("GET_TRANSACTION".equals(apiType)) {
                    String transactionTraceNumber = (String) tc.get("transactionTraceNumber");
                    String fromDate = (String) tc.get("fromDate");
                    String toDate = (String) tc.get("toDate");
                    if (fromDate == null || fromDate.trim().isEmpty()) {
                        fromDate = TestUtils.getDefaultFromDate();
                        LoggerUtil.info("fromDate not specified in test case, using current date: {}", fromDate);
                    }
                    if (toDate == null || toDate.trim().isEmpty()) {
                        toDate = TestUtils.getDefaultToDate();
                        LoggerUtil.info("toDate not specified in test case, using current date: {}", toDate);
                    }

                    dynamicValues.put("transactionTraceNumber", transactionTraceNumber);
                    Allure.addAttachment("Dynamic transactionTraceNumber", transactionTraceNumber);
                    Allure.addAttachment("Dynamic fromDate", fromDate);
                    Allure.addAttachment("Dynamic toDate", toDate);

                    response = TestUtils.callApiAndLogResponse(
                            "Sending transaction history request",
                            "None (GET request)",
                            TransactionTraceAPI.getTransactionHistory(token, transactionTraceNumber, fromDate, toDate)
                    );
                } else if ("GET_BULK_TRANSACTION".equals(apiType)) {
                    String bulkTraceNumber = (String) tc.get("bulkTraceNumber");
                    if (bulkTraceNumber == null || bulkTraceNumber.trim().isEmpty()) {
                        throw new IllegalArgumentException("bulkTraceNumber is required for GET_BULK_TRANSACTION");
                    }

                    String fromDate = (String) tc.get("fromDate");
                    String toDate = (String) tc.get("toDate");
                    if (fromDate == null || fromDate.trim().isEmpty()) {
                        fromDate = TestUtils.getDefaultFromDate();
                        LoggerUtil.info("fromDate not specified in test case, using current date: {}", fromDate);
                    }
                    if (toDate == null || toDate.trim().isEmpty()) {
                        toDate = TestUtils.getDefaultToDate();
                        LoggerUtil.info("toDate not specified in test case, using current date: {}", toDate);
                    }

                    dynamicValues.put("bulkTraceNumber", bulkTraceNumber);
                    Allure.addAttachment("Dynamic bulkTraceNumber", bulkTraceNumber);
                    Allure.addAttachment("Dynamic fromDate", fromDate);
                    Allure.addAttachment("Dynamic toDate", toDate);

                    response = TestUtils.callApiAndLogResponse(
                            "Sending bulk transaction query request",
                            "None (GET request)",
                            BulkTransactionQueryAPI.getBulkTransaction(token, bulkTraceNumber, fromDate, toDate)
                    );

                    List<Map<String, Object>> bulkTransactions = response.jsonPath().getList("responseData.bulkTransactions");
                    if (bulkTransactions == null || bulkTransactions.isEmpty()) {
                        throw new RuntimeException("No bulk transactions found in query response for bulkTraceNumber: " +
                                bulkTraceNumber + " with fromDate: " + fromDate + " and toDate: " + toDate);
                    }

                    String bulkStatus = response.jsonPath().getString("responseData.bulkTransactions[0].bulkStatus");
                    if (bulkStatus == null) {
                        throw new RuntimeException("bulkStatus not found in query bulk transaction response");
                    }
                    assertThat("Bulk transaction status should be COMP", bulkStatus, equalTo("COMP"));
                } else {
                    if (requestTemplateKey == null || requestTemplateKey.trim().isEmpty()) {
                        throw new IllegalArgumentException("requestTemplateKey is required for API type: " + apiType);
                    }
                    response = responseCache.computeIfAbsent(requestTemplateKey + "_" + tc.get("testCaseId"), key -> {
                        try {
                            ObjectNode dynamicRequest = TestUtils.createDynamicRequest(requestTemplateKey, dynamicValues);
                            boolean isBulkTransaction = dynamicRequest.path("requestParameters")
                                    .path("data")
                                    .has("transactions");
                            if (isBulkTransaction) {
                                LoggerUtil.info("Detected bulk transaction template: {}", requestTemplateKey);
                                TestUtils.updateTransactions(dynamicRequest, dynamicValues);
                                return TestUtils.callApiAndLogResponse(
                                        "Sending bulk transaction request for " + requestTemplateKey,
                                        dynamicRequest.toPrettyString(),
                                        BulkTransactionCreateAPI.createBulkTransaction(token, dynamicRequest)
                                );
                            } else {
                                LoggerUtil.info("Detected single transaction template: {}", requestTemplateKey);
                                TestUtils.generateTraceNumber(dynamicRequest, dynamicValues);
                                updateRequestBody(dynamicRequest);
                                return TestUtils.callApiAndLogResponse(
                                        "Sending request for " + requestTemplateKey,
                                        dynamicRequest.toPrettyString(),
                                        PaymentOrderAPI.createPaymentOrder(token, dynamicRequest)
                                );
                            }
                        } catch (Exception e) {
                            LoggerUtil.error("Failed to send request for {}: {}", requestTemplateKey, e.getMessage(), e);
                            Allure.step("Failed to send request: " + e.getMessage(), io.qameta.allure.model.Status.FAILED);
                            throw new RuntimeException("Failed to send request for " + requestTemplateKey, e);
                        }
                    });
                }

                response.then().statusCode(expectedStatusCode);
                TestUtils.validateExpectedField(response, tc, dynamicValues);
            });
        }).collect(Collectors.toList());
    }

    private void updateRequestBody(ObjectNode dynamicRequest) throws Exception {
        ObjectNode requestParameters = (ObjectNode) dynamicRequest.get("requestParameters");
        ObjectNode data = (ObjectNode) requestParameters.get("data");

        requestParameters.get("authorizations").forEach(auth -> {
            String authorizationId = auth.get("authorizationId").asText();
            try {
                LoggerUtil.info("Generating authorizationString for authorizationId: {}", authorizationId);
                Allure.step("Generating authorizationString for authorizationId: " + authorizationId);
                String authorizationString = SignatureUtil.generateAuthorizationString(dynamicRequest, authorizationId);
                ((ObjectNode) auth).put("authorizationString", authorizationString);
                LoggerUtil.info("Updated Request Body after adding authorizationString for authorizationId: {}: \n{}",
                        authorizationId, dynamicRequest.toPrettyString());
                Allure.addAttachment("Updated Request Body after adding authorizationString for authorizationId: " + authorizationId,
                        "application/json", dynamicRequest.toPrettyString());
            } catch (Exception e) {
                LoggerUtil.error("Failed to generate authorizationString for authorizationId: {}", authorizationId, e);
                Allure.step("Failed to generate authorizationString: " + e.getMessage(), io.qameta.allure.model.Status.FAILED);
                throw new RuntimeException("Failed to generate authorizationString", e);
            }
        });
    }
}