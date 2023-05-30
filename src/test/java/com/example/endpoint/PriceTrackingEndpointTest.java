package com.example.endpoint;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static io.restassured.RestAssured.baseURI;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Mahdi Sharifi
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT )
class PriceTrackingEndpointTest {

    @BeforeAll
    public static void setup() {
        baseURI = "http://localhost:8080/service";
    }
    @Test
    void hello() {
        RestAssured
                .get("")
                .then().statusCode(200).assertThat();

    }
}