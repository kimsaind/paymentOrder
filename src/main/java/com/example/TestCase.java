package com.example;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class TestCase {
    @JsonProperty("testCaseId")
    private String testCaseId;

    @JsonProperty("description")
    private String description;

    @JsonProperty("requestTemplateKey")
    private String requestTemplateKey;

    @JsonProperty("expectedStatusCode")
    private int expectedStatusCode;

    @JsonProperty("expectedField")
    private String expectedField;

    @JsonProperty("expectedValue")
    private String expectedValue;

    @JsonProperty("expectedErrorField")
    private String expectedErrorField;

    @JsonProperty("expectedErrorMessage")
    private String expectedErrorMessage;

    @JsonProperty("tags")
    private List<String> tags;

    @JsonProperty("testName")
    private String testName;

    public String getTestCaseId() { return testCaseId; }
    public String getDescription() { return description; }
    public String getRequestTemplateKey() { return requestTemplateKey; }
    public int getExpectedStatusCode() { return expectedStatusCode; }
    public String getExpectedField() { return expectedField; }
    public String getExpectedValue() { return expectedValue; }
    public String getExpectedErrorField() { return expectedErrorField; }
    public String getExpectedErrorMessage() { return expectedErrorMessage; }
    public List<String> getTags() { return tags; }
    public String getTestName() { return testName; }
}