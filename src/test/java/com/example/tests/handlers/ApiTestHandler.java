package com.example.tests.handlers;

import io.restassured.response.Response;

import java.util.Map;

public interface ApiTestHandler {
    Response execute(Map<String, Object> testCase, Map<String, String> dynamicValues) throws Exception;
    void validateResponse(Response response, Map<String, Object> testCase);
}