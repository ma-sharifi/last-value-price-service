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
 */
@Slf4j
public class ConsumeInstrument {
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
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
        List<Instrument> instrumentActualList = PriceDataTestGenerator.readInstrumentJsonFile("./instrument-actual.txt");
        List<Instrument> instrumentExpectedList = PriceDataTestGenerator.readInstrumentJsonFile("./instrument-expected.txt");

        long start = System.currentTimeMillis();
        System.out.println("requestNo/threadsNo: " + requestNo / threadsNo + " ;Thread: " + threadsNo + " pSize: " + partitionSize + " ;recordRandomNo: " + recordRandomNo);
        var threadPool = Executors.newFixedThreadPool(threadsNo);
        for (int i = 0; i < instrumentExpectedList.size(); i++) {
                   Instrument instrument= readInstrument(instrumentExpectedList.get(i).id());
                   if(!instrument.equals(instrumentExpectedList.get(i)))
                       System.out.println("#Server: "+instrument+" ;expected: "+instrumentExpectedList.get(i));
        }
//        try {
//            for (int j = 0; j < requestNo / threadsNo; j++) {
//                var latchUser = new CountDownLatch(threadsNo);
//                for (int i = 0; i < threadsNo; i++) {
//                    System.out.println("#Batch No: " + (i + 1) * (j + 1) + "/" + (threadsNo));
//                    threadPool.execute(() -> {
//                        try {//--------------one batch------
////                            PriceDataTestGenerator.Pair pair = PriceDataTestGenerator.generateRandomPriceDataList(recordRandomNo);
////                            List<Instrument> instrumentList = pair.instrumentMap().values().stream().toList();
//
//                            read(1+"");
//                            System.out.println( );
//
//                            latchUser.countDown();
//                        } catch (Exception exception) {
//                            log.error("#Exception in sending a batch: " + exception.getMessage());
//                        }
//                    });
//                }
//                latchUser.await(10, TimeUnit.MILLISECONDS);// Wait until the count reaches zero/timeout -> threadsNo batch operations are completed here.
//            }
//        } catch (Exception ex) {
//            log.error("#Exception in sending multiple batch: " + ex.getMessage());
//        } finally {
//            threadPool.shutdown();
//            String system = "#max: " + Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MB; free: " + Runtime.getRuntime().freeMemory() / (1024 * 1024) + " MB; total: " + Runtime.getRuntime().totalMemory() / (1024 * 1024) + " MB; core: " + Runtime.getRuntime().availableProcessors();
//            System.out.println("#TIME: " + (System.currentTimeMillis() - start) + " ms ;requestNo/threadsNo: " + requestNo / threadsNo + " ;Thread: " + threadsNo + " pSize: " + partitionSize + " ;recordRandomNo: " + recordRandomNo + " ;" + system);
//        }
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
