package com.example.producer;

import com.example.model.Instrument;
import com.example.model.PriceData;
import com.example.util.PriceDataTestGenerator;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.example.util.PriceDataTestGenerator.*;

/**
 * @author Mahdi Sharifi
 */
@Slf4j
public class ProducePrice {
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient client = new OkHttpClient().newBuilder()
            .connectTimeout(1, TimeUnit.MINUTES)
            .readTimeout(2, TimeUnit.MINUTES)
            .build();
    private static final String URL = "http://localhost:" + 8080 + "/instruments";

    private static final int recordNo = 5000;// Number of PriceData Object
    private static final int partitionSize = 1000;//Number of records in a chunks.

    //Define client users
    private static final int requestNo = 4;
    private static final int threadsNo = 2;

    public static void main(String[] args) throws IOException {
//        List<PriceData> priceDataList = PriceDataTestGenerator.readPriceDataJsonFile("./pricedata.txt");
        Pair pair= PriceDataTestGenerator.generatePriceInstrumentList(recordNo,0);
        List<PriceData> priceDataList = pair.priceDataList();
        List<Instrument> instrumentActualList = pair.instrumentActualList();
//        insertInstruments(instrumentActualList); //Fill storage

        long start = System.currentTimeMillis();
        System.out.println("requestNo/threadsNo: " + requestNo / threadsNo + " ;Thread: " + threadsNo + " pSize: " + partitionSize + " ;recordNo: " + recordNo);
        var threadPool = Executors.newFixedThreadPool(threadsNo);
        try {
            for (int j = 0; j < requestNo / threadsNo; j++) {
                var latchUser = new CountDownLatch(threadsNo);
                for (int i = 0; i < threadsNo; i++) {
                    System.out.println("#Batch No: " + (i + 1) * (j + 1) + "/" + ((requestNo/threadsNo)*threadsNo));
                    final int step=i;
                    threadPool.execute(() -> {
                        try {//--------------one batch------
                            PriceDataTestGenerator.Triple triple=PriceDataTestGenerator.generatePriceInstrumentListTriple(recordNo,step);
                            List<PriceData> prices=triple.priceDataList();
                            List<Instrument> instruments=triple.instrumentExpectedList();
                            insertPriceList(prices,instruments);

                            latchUser.countDown();
                        } catch (Exception exception) {
                            log.error("#Exception in sending a batch: " + exception.getMessage());
                        }
                    });
                }
                latchUser.await(10,TimeUnit.MILLISECONDS);// Wait until the count reaches zero/timeout -> threadsNo batch operations are completed here.
            }
        } catch (Exception ex) {
            log.error("#Exception in sending multiple batch: " + ex.getMessage());
        } finally {
            threadPool.shutdown();
            String system="#max: " + Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MB; free: " + Runtime.getRuntime().freeMemory() / (1024 * 1024) + " MB; total: " + Runtime.getRuntime().totalMemory() / (1024 * 1024) + " MB; core: " + Runtime.getRuntime().availableProcessors();
            System.out.println("#TIME: " + (System.currentTimeMillis() - start) + " ms ;requestNo/threadsNo: " + requestNo / threadsNo + " ;Thread: " + threadsNo + " pSize: " + partitionSize + " ;recordNo: " + recordNo+" ;"+system);
        }
    }

    private static void insertPriceList(List<PriceData> priceDataRandomList,List<Instrument> instrumentExpectedlList) throws IOException {

        //start a batch
        RequestBody bodyStart = RequestBody.create("", JSON);
        String batchId="";
        Request requestStart = new Request.Builder()
                .url(URL + "/batch/start" )
                .post(bodyStart)
                .build();
        try (Response response = client.newCall(requestStart).execute()) {
            batchId = response.body().string();
        }
        System.out.println("#batchId: "+batchId);

        //upload chunks
        for (List<PriceData>  chunk:Lists.partition(priceDataRandomList,partitionSize)) {
            String json = GSON.toJson(chunk);
            RequestBody body = RequestBody.create(json, JSON);
            Request request = new Request.Builder()
                    .url(URL + "/batch/"+batchId+"/upload")
                    .post(body)
                    .build();
            try (Response response = client.newCall(request).execute()) {
                System.out.println(batchId+ " ;upload.code: "+  response.code());
            }
        }
        //batch is completed
        RequestBody bodyComplete = RequestBody.create("", JSON);
        Request requestComplete = new Request.Builder()
                .url(URL + "/batch/"+batchId+"/complete")
                .post(bodyComplete)
                .build();
        try (Response response = client.newCall(requestComplete).execute()) {
            System.out.println(batchId+ " ;complete.code: "+  response.code());
        }

        Request requestCounter = new Request.Builder()
                .url(URL + "/batch/"+batchId+"/counter")
                .get()
                .build();
        try (Response response = client.newCall(requestCounter).execute()) {
            System.out.println(batchId+ " ;counter.code: "+  response.body().string());
        }
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
