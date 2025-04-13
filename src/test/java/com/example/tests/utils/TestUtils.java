package com.example.tests.utils;

import com.example.utils.FileUtil;
import com.example.utils.LoggerUtil;
import com.example.utils.SignatureUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import io.restassured.response.Response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class TestUtils {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Step("Create dynamic request for template: {requestTemplateKey}")
    public static ObjectNode createDynamicRequest(String requestTemplateKey, Map<String, String> dynamicValues) throws Exception {
        Object requestBody = FileUtil.getRequestTemplate(requestTemplateKey);
        ObjectNode dynamicRequest = mapper.valueToTree(requestBody).deepCopy();

        String dynamicRequestTrace = UUID.randomUUID().toString();
        dynamicRequest.put("requestTrace", dynamicRequestTrace);
        dynamicValues.put("requestTrace", dynamicRequestTrace);

        String dynamicTimestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        dynamicRequest.put("requestDateTime", dynamicTimestamp);
        dynamicValues.put("requestDateTime", dynamicTimestamp);

        Allure.addAttachment("Dynamic requestTrace", dynamicRequestTrace);
        Allure.addAttachment("Dynamic requestDateTime", dynamicTimestamp);

        return dynamicRequest;
    }

    @Step("Generate trace number for transaction")
    public static String generateTraceNumber(ObjectNode dynamicRequest, Map<String, String> dynamicValues) {
        String transactionType = dynamicRequest.path("requestParameters")
                .path("data")
                .path("transaction")
                .path("transactionType")
                .asText();
        String traceNumberPrefix;
        switch (transactionType.toLowerCase()) {
            case "insidebank":
                traceNumberPrefix = "inside";
                break;
            case "ibft":
                traceNumberPrefix = "ibft";
                break;
            case "citad":
                traceNumberPrefix = "citad";
                break;
            default:
                traceNumberPrefix = "unknown";
                LoggerUtil.warn("Unknown transactionType: {}, using default prefix 'unknown'", transactionType);
        }
        String traceNumber = traceNumberPrefix + generateRandomString(10);
        ((ObjectNode) dynamicRequest.path("requestParameters").path("data").path("transaction"))
                .put("transactionTraceNumber", traceNumber);
        dynamicValues.put("requestParameters.data.transaction.transactionTraceNumber", traceNumber);
        return traceNumber;
    }

    @Step("Generate bulk trace number for bulk transaction")
    public static String generateBulkTraceNumber(ObjectNode dynamicRequest, Map<String, String> dynamicValues) {
        String bulkTraceNumber = "pika" + generateRandomString(10);
        ((ObjectNode) dynamicRequest.path("requestParameters").path("data"))
                .put("bulkTraceNumber", bulkTraceNumber);
        dynamicValues.put("requestParameters.data.bulkTraceNumber", bulkTraceNumber);
        LoggerUtil.info("Generated bulkTraceNumber: {}", bulkTraceNumber);
        Allure.addAttachment("Generated bulkTraceNumber", bulkTraceNumber);
        return bulkTraceNumber;
    }

    @Step("Update transactions with dynamic trace numbers")
    public static void updateTransactions(ObjectNode dynamicRequest, Map<String, String> dynamicValues) {
        // Tạo bulkTraceNumber trước khi cập nhật transactions
        generateBulkTraceNumber(dynamicRequest, dynamicValues);

        dynamicRequest.path("requestParameters").path("data").path("transactions").forEach(transaction -> {
            String transactionType = transaction.path("transactionType").asText();
            LoggerUtil.info("Processing transaction with transactionType: {}", transactionType);
            String traceNumberPrefix;
            switch (transactionType.toLowerCase()) {
                case "insidebank":
                    traceNumberPrefix = "inside";
                    break;
                case "ibft":
                    traceNumberPrefix = "ibft";
                    break;
                case "citad":
                    traceNumberPrefix = "citad";
                    break;
                default:
                    traceNumberPrefix = "unknown";
                    LoggerUtil.warn("Unknown transactionType: {}, using default prefix 'unknown'", transactionType);
            }
            String traceNumber = traceNumberPrefix + generateRandomString(10);
            LoggerUtil.info("Generated transactionTraceNumber: {}", traceNumber);
            ((ObjectNode) transaction).put("transactionTraceNumber", traceNumber);
            dynamicValues.put("requestParameters.data.transactions.transactionTraceNumber", traceNumber);
        });
    }

    @Step("{stepDescription}")
    public static Response callApiAndLogResponse(String stepDescription, String requestBody, Response response) {
        LoggerUtil.info("Sending Request Body: \n{}", requestBody);
        Allure.addAttachment("Request Body", "application/json", requestBody);
        LoggerUtil.info("Response - Status Code: {}", response.getStatusCode());
        LoggerUtil.info("Response - Headers: {}", response.getHeaders());
        LoggerUtil.info("Response - Body: \n{}", response.getBody().asPrettyString());
        Allure.addAttachment("Response - Status Code", String.valueOf(response.getStatusCode()));
        Allure.addAttachment("Response - Headers", response.getHeaders().toString());
        Allure.addAttachment("Response - Body", "application/json", response.getBody().asPrettyString());
        return response;
    }

    @Step("Validate expected field in response")
    public static void validateExpectedField(Response response, Map<String, Object> testCase, Map<String, String> dynamicValues) {
        String expectedField = (String) testCase.get("expectedField");
        String expectedValue = (String) testCase.get("expectedValue");

        if (expectedField != null) {
            String actualValue;
            if ("responseData.bulkTransactions.bulkStatus".equals(expectedField)) {
                List<String> bulkStatusList = null;
                try {
                    bulkStatusList = response.jsonPath().getList(expectedField);
                } catch (Exception e) {
                    LoggerUtil.info("Field {} is not a List, trying to get as String: {}", expectedField, e.getMessage());
                }
                if (bulkStatusList != null && !bulkStatusList.isEmpty()) {
                    actualValue = bulkStatusList.get(0);
                    LoggerUtil.info("Extracted bulkStatus as List from response: {}", actualValue);
                } else {
                    actualValue = response.jsonPath().getString(expectedField);
                    LoggerUtil.info("Extracted bulkStatus as String from response: {}", actualValue);
                }
            } else {
                actualValue = response.jsonPath().getString(expectedField);
            }

            if (actualValue == null) {
                throw new RuntimeException("Field " + expectedField + " not found in response");
            }

            String dynamicValue = dynamicValues.get(expectedField);
            if (dynamicValue != null) {
                assertThat("Response " + expectedField + " should match request value", actualValue, equalTo(dynamicValue));
            } else if (expectedValue != null) {
                assertThat("Response " + expectedField + " should match expected value", actualValue, equalTo(expectedValue));
            }
        }
    }

    @Step("Update request body with authorization string")
    public static void updateRequestBody(ObjectNode dynamicRequest) throws Exception {
        ObjectNode requestParameters = (ObjectNode) dynamicRequest.get("requestParameters");
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

    public static String generateRandomString(int length) {
        String characters = "0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(characters.charAt(random.nextInt(characters.length())));
        }
        return sb.toString();
    }

    public static String getDefaultFromDate() {
        return LocalDate.now().format(DATE_FORMATTER);
    }

    public static String getDefaultToDate() {
        return LocalDate.now().format(DATE_FORMATTER);
    }
}