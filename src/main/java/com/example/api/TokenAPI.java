package com.example.api;

import com.example.config.APIConfig;
import com.example.utils.LoggerUtil;
import io.restassured.response.Response;
import static io.restassured.RestAssured.given;

public class TokenAPI {
    public static String getAccessToken() {
        LoggerUtil.info("Sending Token Request to: {}", APIConfig.TOKEN_ENDPOINT);
        Response response = given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("client_id", APIConfig.CLIENT_ID)
                .formParam("client_secret", APIConfig.CLIENT_SECRET)
                .formParam("grant_type", "client_credentials")
                .post(APIConfig.TOKEN_ENDPOINT);

        response.then().statusCode(200);
        String token = response.jsonPath().getString("access_token");
        LoggerUtil.info("Access Token: {}", token);
        return token;
    }
}