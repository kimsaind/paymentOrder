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

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Component("CREATE_AND_QUERY")
public class CreateAndQueryHandler implements ApiTestHandler {
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
        ObjectNode dynamicRequest = TestUtils.createDynamicRequest(requestTemplateKey, dynamicValues);
        String traceNumber = TestUtils.generateTraceNumber(dynamicRequest, dynamicValues);
        TestUtils.updateRequestBody(dynamicRequest);

        Allure.addAttachment("Dynamic transactionTraceNumber", traceNumber);
        Response createResponse = TestUtils.callApiAndLogResponse(
                "Step 1: Sending create payment order request",
                dynamicRequest.toPrettyString(),
                PaymentOrderAPI.createPaymentOrder(token, dynamicRequest)
        );

        createResponse.then().statusCode(200);
        String status = createResponse.jsonPath().getString("responseData.transaction.status");
        assertThat("Payment order status should be ORIG", status, equalTo("ORIG"));

        String createdTraceNumber = createResponse.jsonPath().getString("responseData.transaction.transactionTraceNumber");
        if (createdTraceNumber == null) {
            throw new RuntimeException("transactionTraceNumber not found in create payment order response");
        }
        Allure.addAttachment("Created transactionTraceNumber", createdTraceNumber);

        Allure.step("Waiting 5 seconds before querying transaction history");
        Thread.sleep(5000);

        String fromDate = TestUtils.getDefaultFromDate();
        String toDate = TestUtils.getDefaultToDate();
        dynamicValues.put("transactionTraceNumber", createdTraceNumber);
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
        String queryStatus = response.jsonPath().getString("responseData.transactions[0].status");
        assertThat("Transaction history status should be TRAN", queryStatus, equalTo("TRAN"));
    }
}