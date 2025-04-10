package com.example.tests;

import com.example.api.PaymentOrderAPI;
import com.example.api.TokenAPI;
import com.example.api.TransactionTraceAPI;
import com.example.utils.FileUtil;
import com.example.utils.LoggerUtil;
import com.example.utils.SignatureUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.qameta.allure.Allure;
import io.restassured.response.Response;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class APITests {
    private static final Map<String, Response> responseCache = new HashMap<>();
    private static final String token = TokenAPI.getAccessToken();
    private static final ObjectMapper mapper = new ObjectMapper();

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

                Allure.step("Test Case ID: " + tc.get("testCaseId"));
                Allure.step("Description: " + tc.get("description"));

                Map<String, String> dynamicValues = new HashMap<>();
                Response response;

                if ("CREATE_AND_QUERY".equals(apiType)) {
                    // Bước 1: Tạo payment order
                    Object requestBody = FileUtil.getRequestTemplate(requestTemplateKey);
                    ObjectNode dynamicRequest = mapper.valueToTree(requestBody).deepCopy();

                    String dynamicRequestTrace = UUID.randomUUID().toString();
                    dynamicRequest.put("requestTrace", dynamicRequestTrace);
                    dynamicValues.put("requestTrace", dynamicRequestTrace);

                    String dynamicTimestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                    dynamicRequest.put("requestDateTime", dynamicTimestamp);
                    dynamicValues.put("requestDateTime", dynamicTimestamp);

                    String traceNumber = "TRACE" + System.currentTimeMillis() + (int) (Math.random() * 1000);
                    ((ObjectNode) dynamicRequest.path("requestParameters").path("data").path("transaction"))
                            .put("transactionTraceNumber", traceNumber);
                    dynamicValues.put("requestParameters.data.transaction.transactionTraceNumber", traceNumber);

                    // Sửa request body và tạo authorizationString
                    updateRequestBody(dynamicRequest);

                    Allure.addAttachment("Dynamic requestTrace", dynamicRequestTrace);
                    Allure.addAttachment("Dynamic requestDateTime", dynamicTimestamp);
                    Allure.addAttachment("Dynamic transactionTraceNumber", traceNumber);

                    Allure.step("Step 1: Sending create payment order request");
                    LoggerUtil.info("Sending Payment Order Request Body: \n{}", dynamicRequest.toPrettyString());
                    Allure.addAttachment("Payment Order Request Body", "application/json", dynamicRequest.toPrettyString());
                    Response createResponse = PaymentOrderAPI.createPaymentOrder(token, dynamicRequest);
                    LoggerUtil.info("Payment Order Response - Status Code: {}", createResponse.getStatusCode());
                    LoggerUtil.info("Payment Order Response - Headers: {}", createResponse.getHeaders());
                    LoggerUtil.info("Payment Order Response - Body: \n{}", createResponse.getBody().asPrettyString());
                    Allure.addAttachment("Payment Order Response - Status Code", String.valueOf(createResponse.getStatusCode()));
                    Allure.addAttachment("Payment Order Response - Headers", createResponse.getHeaders().toString());
                    Allure.addAttachment("Payment Order Response - Body", "application/json", createResponse.getBody().asPrettyString());

                    createResponse.then().statusCode(200);  // Kiểm tra POST thành công

                    // Validate status là ORIG
                    String status = createResponse.jsonPath().getString("responseData.transaction.status");
                    assertThat("Payment order status should be ORIG", status, equalTo("ORIG"));

                    // Lấy transactionTraceNumber từ response
                    String createdTraceNumber = createResponse.jsonPath()
                            .getString("responseData.transaction.transactionTraceNumber");
                    if (createdTraceNumber == null) {
                        throw new RuntimeException("transactionTraceNumber not found in create payment order response");
                    }
                    Allure.addAttachment("Created transactionTraceNumber", createdTraceNumber);

                    // Chờ 5 giây trước khi truy vấn
                    Allure.step("Waiting 5 seconds before querying transaction history");
                    Thread.sleep(5000);

                    // Bước 2: Truy vấn transaction history
                    String fromDate = (String) tc.get("fromDate");
                    String toDate = (String) tc.get("toDate");

                    dynamicValues.put("transactionTraceNumber", createdTraceNumber);
                    Allure.addAttachment("Query transactionTraceNumber", createdTraceNumber);
                    Allure.addAttachment("Query fromDate", fromDate);
                    Allure.addAttachment("Query toDate", toDate);

                    Allure.step("Step 2: Querying transaction history with created trace");
                    LoggerUtil.info("Sending Transaction History Request - transactionTraceNumber: {}, fromDate: {}, toDate: {}",
                            createdTraceNumber, fromDate, toDate);
                    LoggerUtil.info("Transaction History Request Body: None (GET request)");
                    Allure.addAttachment("Transaction History Request - Query Params",
                            "transactionTraceNumber: " + createdTraceNumber + "\nfromDate: " + fromDate + "\ntoDate: " + toDate);
                    Allure.addAttachment("Transaction History Request Body", "None (GET request)");
                    response = TransactionTraceAPI.getTransactionHistory(token, createdTraceNumber, fromDate, toDate);
                    LoggerUtil.info("Transaction History Response - Status Code: {}", response.getStatusCode());
                    LoggerUtil.info("Transaction History Response - Headers: {}", response.getHeaders());
                    LoggerUtil.info("Transaction History Response - Body: \n{}", response.getBody().asPrettyString());
                    Allure.addAttachment("Transaction History Response - Status Code", String.valueOf(response.getStatusCode()));
                    Allure.addAttachment("Transaction History Response - Headers", response.getHeaders().toString());
                    Allure.addAttachment("Transaction History Response - Body", "application/json", response.getBody().asPrettyString());

                    response.then().statusCode(200);  // Kiểm tra GET thành công

                    // Validate status là TRAN trong response GET
                    String queryStatus = response.jsonPath().getString("responseData.transactions[0].status");
                    assertThat("Transaction history status should be TRAN", queryStatus, equalTo("TRAN"));
                } else if ("GET_TRANSACTION".equals(apiType)) {
                    String transactionTraceNumber = (String) tc.get("transactionTraceNumber");
                    String fromDate = (String) tc.get("fromDate");
                    String toDate = (String) tc.get("toDate");

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
                    response = TransactionTraceAPI.getTransactionHistory(token, transactionTraceNumber, fromDate, toDate);
                    LoggerUtil.info("Transaction History Response - Status Code: {}", response.getStatusCode());
                    LoggerUtil.info("Transaction History Response - Headers: {}", response.getHeaders());
                    LoggerUtil.info("Transaction History Response - Body: \n{}", response.getBody().asPrettyString());
                    Allure.addAttachment("Transaction History Response - Status Code", String.valueOf(response.getStatusCode()));
                    Allure.addAttachment("Transaction History Response - Headers", response.getHeaders().toString());
                    Allure.addAttachment("Transaction History Response - Body", "application/json", response.getBody().asPrettyString());
                } else {
                    response = responseCache.computeIfAbsent(requestTemplateKey + "_" + tc.get("testCaseId"), key -> {
                        try {
                            Object requestBody = FileUtil.getRequestTemplate(requestTemplateKey);
                            ObjectNode dynamicRequest = mapper.valueToTree(requestBody).deepCopy();

                            String dynamicRequestTrace = UUID.randomUUID().toString();
                            dynamicRequest.put("requestTrace", dynamicRequestTrace);
                            dynamicValues.put("requestTrace", dynamicRequestTrace);

                            String dynamicTimestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                            dynamicRequest.put("requestDateTime", dynamicTimestamp);
                            dynamicValues.put("requestDateTime", dynamicTimestamp);

                            String traceNumber = "TRACE" + System.currentTimeMillis() + (int) (Math.random() * 1000);
                            ((ObjectNode) dynamicRequest.path("requestParameters").path("data").path("transaction"))
                                    .put("transactionTraceNumber", traceNumber);
                            dynamicValues.put("requestParameters.data.transaction.transactionTraceNumber", traceNumber);

                            // Sửa request body và tạo authorizationString
                            updateRequestBody(dynamicRequest);

                            Allure.addAttachment("Dynamic requestTrace", dynamicRequestTrace);
                            Allure.addAttachment("Dynamic requestDateTime", dynamicTimestamp);
                            Allure.addAttachment("Dynamic transactionTraceNumber", traceNumber);

                            Allure.step("Sending request for " + requestTemplateKey);
                            LoggerUtil.info("Sending Payment Order Request Body: \n{}", dynamicRequest.toPrettyString());
                            Allure.addAttachment("Payment Order Request Body", "application/json", dynamicRequest.toPrettyString());
                            Response createResponse = PaymentOrderAPI.createPaymentOrder(token, dynamicRequest);
                            LoggerUtil.info("Payment Order Response - Status Code: {}", createResponse.getStatusCode());
                            LoggerUtil.info("Payment Order Response - Headers: {}", createResponse.getHeaders());
                            LoggerUtil.info("Payment Order Response - Body: \n{}", createResponse.getBody().asPrettyString());
                            Allure.addAttachment("Payment Order Response - Status Code", String.valueOf(createResponse.getStatusCode()));
                            Allure.addAttachment("Payment Order Response - Headers", createResponse.getHeaders().toString());
                            Allure.addAttachment("Payment Order Response - Body", "application/json", createResponse.getBody().asPrettyString());
                            return createResponse;
                        } catch (Exception e) {
                            Allure.step("Failed to send request: " + e.getMessage(), io.qameta.allure.model.Status.FAILED);
                            throw new RuntimeException("Failed to send request for " + requestTemplateKey, e);
                        }
                    });
                }

                response.then().statusCode(expectedStatusCode);

                if (expectedField != null) {
                    String actualValue = response.jsonPath().getString(expectedField);
                    String dynamicValue = dynamicValues.get(expectedField);
                    if (dynamicValue != null) {
                        assertThat("Response " + expectedField + " should match request value",
                                actualValue, equalTo(dynamicValue));
                    } else if (expectedValue != null) {
                        assertThat("Response " + expectedField + " should match expected value",
                                actualValue, equalTo(expectedValue));
                    }
                }
            });
        }).collect(Collectors.toList());
    }

    private void updateRequestBody(ObjectNode dynamicRequest) throws Exception {
        ObjectNode requestParameters = (ObjectNode) dynamicRequest.get("requestParameters");
        ObjectNode data = (ObjectNode) requestParameters.get("data");

        // Tạo authorizationString cho từng authorizationId
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