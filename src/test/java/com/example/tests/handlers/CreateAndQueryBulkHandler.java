package com.example.tests.handlers;

import com.example.api.BulkTransactionCreateAPI;
import com.example.api.BulkTransactionQueryAPI;
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

@Component("CREATE_AND_QUERY_BULK")
public class CreateAndQueryBulkHandler implements ApiTestHandler {
    private final String token;
    private final ObjectMapper mapper;

    @Autowired
    public CreateAndQueryBulkHandler(ObjectMapper mapper) {
        this.token = TokenAPI.getAccessToken();
        LoggerUtil.info("Access token in CreateAndQueryBulkHandler: {}", token);
        this.mapper = mapper;
    }

    @Override
    @Step("Execute CREATE_AND_QUERY_BULK test case")
    public Response execute(Map<String, Object> testCase, Map<String, String> dynamicValues) throws Exception {
        String requestTemplateKey = (String) testCase.get("requestTemplateKey");
        ObjectNode dynamicRequest = TestUtils.createDynamicRequest(requestTemplateKey, dynamicValues);
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

        Allure.step("Waiting 7 seconds before querying bulk transaction");
        Thread.sleep(7000);

        String fromDate = (String) testCase.get("fromDate");
        String toDate = (String) testCase.get("toDate");
        if (fromDate == null || fromDate.trim().isEmpty()) {
            fromDate = TestUtils.getDefaultFromDate();
            LoggerUtil.info("fromDate not specified in test case, using current date: {}", fromDate);
        }
        if (toDate == null || toDate.trim().isEmpty()) {
            toDate = TestUtils.getDefaultToDate();
            LoggerUtil.info("toDate not specified in test case, using current date: {}", toDate);
        }

        dynamicValues.put("bulkTraceNumber", bulkTraceNumber);
        Allure.addAttachment("Query bulkTraceNumber", bulkTraceNumber);
        Allure.addAttachment("Query fromDate", fromDate);
        Allure.addAttachment("Query toDate", toDate);

        LoggerUtil.info("Sending Bulk Transaction Query Request - bulkTraceNumber: {}, fromDate: {}, toDate: {}",
                bulkTraceNumber, fromDate, toDate);
        LoggerUtil.info("Bulk Transaction Query Request Body: None (GET request)");
        Allure.addAttachment("Bulk Transaction Query Request - Query Params",
                "bulkTraceNumber: " + bulkTraceNumber + "\nfromDate: " + fromDate + "\ntoDate: " + toDate);
        Allure.addAttachment("Bulk Transaction Query Request Body", "None (GET request)");

        Response response = BulkTransactionQueryAPI.getBulkTransaction(token, bulkTraceNumber, fromDate, toDate);
        return TestUtils.callApiAndLogResponse(
                "Step 2: Querying bulk transaction with created bulkTraceNumber",
                "None (GET request)",
                response
        );
    }

    @Override
    @Step("Validate response for CREATE_AND_QUERY_BULK test case")
    public void validateResponse(Response response, Map<String, Object> testCase) {
        response.then().statusCode((Integer) testCase.get("expectedStatusCode"));
        String queryStatus = response.jsonPath().getString("responseData.bulkTransactions[0].bulkStatus");
        if (queryStatus == null) {
            throw new RuntimeException("bulkStatus not found in query bulk transaction response");
        }
        assertThat("Bulk transaction query status should be COMP", queryStatus, equalTo("COMP"));
    }
}