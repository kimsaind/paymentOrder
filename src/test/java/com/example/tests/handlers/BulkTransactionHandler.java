package com.example.tests.handlers;

import com.example.api.BulkTransactionCreateAPI;
import com.example.api.TokenAPI;
import com.example.tests.utils.TestUtils;
import com.example.utils.LoggerUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Component("BULK_TRANSACTION")
public class BulkTransactionHandler implements ApiTestHandler {
    private final String token;
    private final ObjectMapper mapper;

    @Autowired
    public BulkTransactionHandler(ObjectMapper mapper) {
        this.token = TokenAPI.getAccessToken();
        LoggerUtil.info("Access token in BulkTransactionHandler: {}", token);
        this.mapper = mapper;
    }

    @Override
    @Step("Execute BULK_TRANSACTION test case")
    public Response execute(Map<String, Object> testCase, Map<String, String> dynamicValues) throws Exception {
        String requestTemplateKey = (String) testCase.get("requestTemplateKey");
        ObjectNode dynamicRequest = TestUtils.createDynamicRequest(requestTemplateKey, dynamicValues);

        // Tạo bulkTraceNumber động
        String bulkTraceNumber = "pika" + TestUtils.generateRandomString(10);
        ((ObjectNode) dynamicRequest.path("requestParameters").path("data"))
                .put("bulkTraceNumber", bulkTraceNumber);
        dynamicValues.put("requestParameters.data.bulkTraceNumber", bulkTraceNumber);

        TestUtils.updateTransactions(dynamicRequest, dynamicValues);
        TestUtils.updateRequestBody(dynamicRequest);

        Allure.addAttachment("Dynamic bulkTraceNumber", bulkTraceNumber);
        Response createResponse = TestUtils.callApiAndLogResponse(
                "Step 1: Sending bulk transaction create request",
                dynamicRequest.toPrettyString(),
                BulkTransactionCreateAPI.createBulkTransaction(token, dynamicRequest)
        );

        createResponse.then().statusCode(200);
        String createStatus = createResponse.jsonPath().getString("responseData.bulkTransaction.bulkStatus");
        if (createStatus == null) {
            throw new RuntimeException("bulkStatus not found in create bulk transaction response");
        }
        assertThat("Bulk transaction create status should be ORIG", createStatus, equalTo("ORIG"));

        return createResponse;
    }

    @Override
    @Step("Validate response for BULK_TRANSACTION test case")
    public void validateResponse(Response response, Map<String, Object> testCase) {
        response.then().statusCode((Integer) testCase.get("expectedStatusCode"));
        TestUtils.validateExpectedField(response, testCase, new java.util.HashMap<>());
    }
}