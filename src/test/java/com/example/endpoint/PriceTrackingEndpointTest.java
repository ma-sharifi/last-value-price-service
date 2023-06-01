package com.example.endpoint;

import com.example.model.Instrument;
import com.example.model.PriceData;
import com.example.util.PriceDataTestGenerator;
import com.google.common.collect.Lists;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.example.util.PriceDataTestGenerator.generatePriceInstrumentList;
import static io.restassured.RestAssured.baseURI;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * @author Mahdi Sharifi
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PriceTrackingEndpointTest {

    @LocalServerPort
    int port;

    @BeforeEach
    public void setup() {

        baseURI = "http://localhost:" + port + "/instruments";
    }

    private static final int recordRandomNo = 500;// Number of PriceData Object
    private static final int partitionSize = 100;//Number of records in a chunks.

    //Define client users
    private static final int threadsNo = 5;

    @Test
    void shouldTestProduceAndConsumer() throws InterruptedException {
        long start = System.currentTimeMillis();
        PriceDataTestGenerator.Pair pair = generatePriceInstrumentList(recordRandomNo, 1);

        List<PriceData> priceDataList = pair.priceDataList();
        List<Instrument> instrumentActualList = pair.instrumentActualList();

        //insert instrument data into storage
        long recordInserted = RestAssured
                .given()
                .contentType("application/json")
                .body(instrumentActualList)
                .when()
                .post("/")
                .then()
                .statusCode(HttpStatus.CREATED.value())
                .assertThat().extract().as(Long.class);

        List<Instrument> instrumentExpectedList = new ArrayList<>();
        //------------------------------------------------
        ExecutorService executorService = Executors.newFixedThreadPool(threadsNo);
        for (int i = 1; i <= threadsNo; i++) {
            var latchUser = new CountDownLatch(threadsNo);
            final int step = i;
            executorService.execute(() -> {
                List<PriceData> temp = List.copyOf(priceDataList);
                instrumentExpectedList.clear();
                priceDataList.clear();
                for (PriceData priceData : temp) {
                    var asOfNew = LocalDateTime.ofEpochSecond(priceData.asOf().toEpochSecond(ZoneOffset.UTC) + 10, 0, ZoneOffset.UTC);// we can use random: generateRandomDateTime(1000000);
                    var priceDataNew = new PriceData(priceData.id(), asOfNew, priceData.payload() + 1);//Generate PriceData with time after that instrument
                    priceDataList.add(priceDataNew);
                    instrumentExpectedList.add(new Instrument(priceDataNew.id(), priceDataNew.asOf(), priceDataNew.payload()));
                }
                System.out.println("#Batch: " + step + "/" + threadsNo);
                System.out.println("#Price is updating... Current price: " + (priceDataList.get(0).payload()) + " ;total record no is: " + recordRandomNo);
                startUploadComplete(priceDataList);
                System.out.println("#Price is updated! Current price: " + (priceDataList.get(0).payload()) + " ;total record no is: " + recordRandomNo);
                //consumer asserts the result with expected result
                for (int k = 0; k < instrumentExpectedList.size(); k++) {
                    Instrument instrumentActual = RestAssured
                            .given()
                            .accept("application/json")
                            .pathParams("instrumentId", k)
                            .get("/{instrumentId}")
                            .then().statusCode(200).assertThat().extract().as(Instrument.class);
                    assertThat(instrumentActual).isEqualTo(instrumentExpectedList.get(k));
                }
                System.out.println("#Price is freezed: " + (priceDataList.get(0).payload()));
                startUploadComplete(priceDataList);
                //consumer asserts the result with expected result
                for (int k = 0; k < instrumentExpectedList.size(); k++) {
                    Instrument instrumentActual = RestAssured
                            .given()
                            .accept("application/json")
                            .pathParams("instrumentId", k)
                            .get("/{instrumentId}")
                            .then().statusCode(200).assertThat().extract().as(Instrument.class);
                    assertThat(instrumentActual).isEqualTo(instrumentExpectedList.get(k));
                }
                latchUser.countDown();

            });
            latchUser.await(1, TimeUnit.MINUTES);
        }
        executorService.shutdown();
        String system = "#max: " + Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MB; free: " + Runtime.getRuntime().freeMemory() / (1024 * 1024) + " MB; total: " + Runtime.getRuntime().totalMemory() / (1024 * 1024) + " MB; core: " + Runtime.getRuntime().availableProcessors();
        System.out.println("#TIME: " + (System.currentTimeMillis() - start) + " ms" + " ;Thread: " + threadsNo + " pSize: " + partitionSize + " ;recordRandomNo: " + recordRandomNo + " ;" + system);
    }

    private void startUploadComplete(List<PriceData> priceDataList) {
        //start the batch
        String batchId = RestAssured
                .post("/batch/start")
                .then().statusCode(200)
                .assertThat().extract().asString();

        //upload a priceData batch once :-) update the instrument price
        List<List<PriceData>> partitionedDataLocal = Lists.partition(priceDataList, partitionSize);

        for (List<PriceData> chunk : partitionedDataLocal) {
            RestAssured
                    .given()
                    .contentType("application/json")
                    .pathParams("batchId", batchId)
                    .body(chunk)
                    .when()
                    .post("/batch/{batchId}/upload")
                    .then()
                    .statusCode(HttpStatus.ACCEPTED.value())
                    .assertThat();
        }

        //finish the batch
        RestAssured
                .given()
                .contentType("application/json")
                .pathParams("batchId", batchId)
                .when()
                .post("/batch/{batchId}/complete")
                .then()
                .statusCode(HttpStatus.CREATED.value())
                .assertThat();

        int updateCounter = RestAssured
                .given()
                .pathParams("batchId", batchId)
                .get("/batch/{batchId}/counter")
                .then().statusCode(200)
                .assertThat().extract().as(Integer.class);
        System.out.println("#updateCounter: " + updateCounter);
    }

}