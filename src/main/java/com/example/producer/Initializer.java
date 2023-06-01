package com.example.producer;

import com.example.model.Instrument;
import com.example.util.PriceDataTestGenerator;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.example.util.PriceDataTestGenerator.GSON;

/**
 * @author Mahdi Sharifi
 * It put the data into our in memory storage/database
 */
@Slf4j
public class Initializer {
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient client = new OkHttpClient().newBuilder()
            .connectTimeout(1, TimeUnit.MINUTES)
            .readTimeout(2, TimeUnit.MINUTES)
            .build();
    private static final String URL = "http://localhost:" + 8080 + "/instruments";

    private static final int recordRandomNo = 5000;// Number of PriceData Object

    public static void main(String[] args) throws IOException {
        //generate json file that consumer and producer use them for uploading and consuming objects
        PriceDataTestGenerator.Pair pair = PriceDataTestGenerator.generateRandomPriceDataListSaveFile(recordRandomNo,1);
        insertInstruments(pair.instrumentActualList());
    }

    private static void insertInstruments(List<Instrument> instrumentList) throws IOException {
        String json = GSON.toJson(instrumentList);

        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
                .url(URL)
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            String result = response.body().string();
            log.info("#insert-data-size: "+result);
        }
    }
}
