package com.example.producer;

import com.example.model.Instrument;
import com.example.util.PriceDataTestGenerator;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.example.util.PriceDataTestGenerator.GSON;
import static com.example.util.PriceDataTestGenerator.generateRandomPriceDataList;

/**
 * @author Mahdi Sharifi
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
        PriceDataTestGenerator.Pair pair = generateRandomPriceDataList(recordRandomNo,1);
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
            System.out.println("#insert-data-size: "+result);
        }
    }
}
