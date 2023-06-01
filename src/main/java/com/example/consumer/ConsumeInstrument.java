package com.example.consumer;

import com.example.model.Instrument;
import com.example.util.PriceDataTestGenerator;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author Mahdi Sharifi
 * It must call after Producer call
 */
@Slf4j
public class ConsumeInstrument {
    private static final OkHttpClient client = new OkHttpClient().newBuilder()
            .connectTimeout(1, TimeUnit.MINUTES)
            .readTimeout(2, TimeUnit.MINUTES)
            .build();
    private static final String URL = "http://localhost:" + 8080 + "/instruments";

    private static final int recordRandomNo = 5000;// Number of PriceData Object
    private static final int partitionSize = 1000;//Number of records in a chunks.

    //Define client users
    private static final int requestNo = 1;
    private static final int threadsNo = 1;

    public static void main(String[] args) throws IOException {
        List<Instrument> instrumentExpectedList = PriceDataTestGenerator.readInstrumentJsonFile("./instrument-expected.txt");

        long start = System.currentTimeMillis();
        log.info("requestNo/threadsNo: " + requestNo / threadsNo + " ;Thread: " + threadsNo + " pSize: " + partitionSize + " ;recordRandomNo: " + recordRandomNo);
        var threadPool = Executors.newFixedThreadPool(threadsNo);
        for (int i = 0; i < instrumentExpectedList.size(); i++) {
            Instrument instrument = readInstrument(instrumentExpectedList.get(i).id());
            if (!instrument.equals(instrumentExpectedList.get(i)))
                log.info("#Server: " + instrument + " ;expected: " + instrumentExpectedList.get(i));
        }
    }
    private static Instrument readInstrument(String instrumentId) throws IOException {
        Request request = new Request.Builder()
                .url(URL + "/" + instrumentId)
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            var json = response.body().string();
            return PriceDataTestGenerator.GSON.fromJson(json, Instrument.class);
        }
    }
}
