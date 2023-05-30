package com.example.producer;

import com.example.model.Instrument;
import com.example.model.PriceData;
import com.example.util.PriceDataTestGenerator;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    private static final int recordRandomNo = 5000;// Number of PriceData Object
    private static final int partitionSize = 1000;//Number of records in a chunks.

    //Define client users
    private static final int requestNo = 1000;
    private static final int threadsNo = 100;

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        System.out.println("requestNo/threadsNo: " + requestNo / threadsNo + " ;Thread: " + threadsNo + " pSize: " + partitionSize + " ;recordRandomNo: " + recordRandomNo);
        var threadPool = Executors.newFixedThreadPool(threadsNo);
        try {
            for (int j = 0; j < requestNo / threadsNo; j++) {
                var latchUser = new CountDownLatch(threadsNo);
                for (int i = 0; i < threadsNo; i++) {
                    System.out.println("#Batch No: " + (i + 1) * (j + 1) + "/" + ((requestNo/threadsNo)*threadsNo));
                    threadPool.execute(() -> {
                        try {//--------------one batch------
                            PriceDataTestGenerator.Pair pair = PriceDataTestGenerator.generateRandomPriceDataList(recordRandomNo);
                            List<Instrument> instrumentList = pair.instrumentMap().values().stream().toList();
                            insertInstruments(instrumentList);

                            List<PriceData> priceDataRandomList = pair.priceDataList();
                            insertPriceList(priceDataRandomList);

                            latchUser.countDown();
                        } catch (Exception exception) {
                            log.error("#Exception in sending a batch: " + exception.getMessage());
                        }
                    });
                }
                latchUser.await();// Wait until the count reaches zero -> threadsNo batch operations are completed here.
            }
        } catch (Exception ex) {
            log.error("#Exception in sending multiple batch: " + ex.getMessage());
        } finally {
            threadPool.shutdown();
            System.out.println("#maxMemory2: " + Runtime.getRuntime().maxMemory() / (1024 * 1024) + " ;free: " + Runtime.getRuntime().freeMemory() / (1024 * 1024) + " ;" + Runtime.getRuntime().totalMemory() / (1024 * 1024) + " ;" + Runtime.getRuntime().availableProcessors());
            System.out.println("#TIME: " + (System.currentTimeMillis() - start) + " ms ;requestNo/threadsNo: " + requestNo / threadsNo + " ;Thread: " + threadsNo + " pSize: " + partitionSize + " ;recordRandomNo: " + recordRandomNo);
        }

    }

    private static void insertPriceList(List<PriceData> priceDataRandomList) throws IOException {
        String json = PriceDataTestGenerator.GSON.toJson(priceDataRandomList);

        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
                .url(URL + "/load?psize=" + partitionSize)
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            String result = response.body().string();
            if (recordRandomNo != Integer.parseInt(result))
                log.info("#MUTEX ERROR! Rest.result: " + result + " ;recordRandomNo: " + recordRandomNo);
        }
    }

    private static void insertInstruments(List<Instrument> instrumentList) throws IOException {
        String json = PriceDataTestGenerator.GSON.toJson(instrumentList);

        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
                .url(URL)
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            String result = response.body().string();
//            System.out.println("#insert-data-size: "+result);
        }
    }

//    static void generateMockData() {
//        PriceDataTestGenerator.Pair pair = PriceDataTestGenerator.generateRandomPriceDataList(recordRandomNo);
//        priceDataRandomList = pair.priceDataList();
//        List<List<PriceData>> partitionedData = Lists.partition(priceDataRandomList, partitionSize);
//        instrumentRandomMap = pair.instrumentMap();
//        int counter = 0;
//        for (List<PriceData> partitionedDatum : partitionedData) {
//            priceDataChunkMap.put(counter, List.copyOf(partitionedDatum)); // Add partitioned list to WeakHashMap as a temporary repository. Deleted object will be garbage collected in the next GC cycle.
//            counter++;
//        }
//    }
}
