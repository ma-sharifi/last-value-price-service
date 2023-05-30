package com.example.service;

import com.example.model.Instrument;
import com.example.model.PriceData;
import com.example.util.PriceDataTestGenerator;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @see InMemoryPriceTrackingService
 */
@SpringBootTest
@Slf4j
class InMemoryPriceTrackingServiceTest {

    @Autowired
    private PriceTrackingService service;

    private static final Map<Integer, List<PriceData>> priceDataChunkMap = new WeakHashMap<>(); // defined as a field due to aggregate the messages.
    private static Map<String, Instrument> instrumentRandomMap = new WeakHashMap<>(); // defined as a field due to aggregate the messages.
    private static List<PriceData> priceDataRandomList = new ArrayList<>(); // defined as a field due to aggregate the messages.

    static final int recordRandomNo = 1000;// Number of PriceData Object
    static final int partitionSize = 100;//Number of records in a chunks.

    //Define client users
    static final int requestNo = 10;
    static final int threadsNo = 5; // Number of thread(users)

    private final List<PriceData> priceDataFixedList = List.of(
            new PriceData("1", LocalDateTime.of(LocalDate.of(2010, 1, 1), LocalTime.of(1, 1, 1)), 1),//Update needed
            new PriceData("2", LocalDateTime.of(LocalDate.of(2010, 1, 1), LocalTime.of(1, 1, 1)), 2),//Update needed
            new PriceData("3", LocalDateTime.of(LocalDate.of(2011, 1, 1), LocalTime.of(1, 1, 1)), 33),//Update needed
            new PriceData("3", LocalDateTime.of(LocalDate.of(2010, 1, 1), LocalTime.of(1, 1, 1)), 3),//No Need!
            new PriceData("2", LocalDateTime.of(LocalDate.of(2009, 1, 1), LocalTime.of(1, 1, 1)), 222),//No Need
            new PriceData("1", LocalDateTime.of(LocalDate.of(2020, 1, 1), LocalTime.of(1, 1, 1)), 11),//Update needed
            new PriceData("2", LocalDateTime.of(LocalDate.of(2020, 1, 1), LocalTime.of(1, 1, 1)), 22),//Update needed
            new PriceData("1", LocalDateTime.of(LocalDate.of(2009, 1, 1), LocalTime.of(1, 1, 1)), 111)//No Need
    );
    //Move them into properties file
    private final Map<String, Instrument> instrumentFixedMap = Map.ofEntries(
            Map.entry("1", new Instrument("1", LocalDateTime.of(LocalDate.of(2000, 1, 1), LocalTime.of(1, 1, 1)), 100)),
            Map.entry("2", new Instrument("2", LocalDateTime.of(LocalDate.of(2000, 1, 1), LocalTime.of(1, 1, 1)), 200)),
            Map.entry("3", new Instrument("3", LocalDateTime.of(LocalDate.of(2000, 1, 1), LocalTime.of(1, 1, 1)), 300)),
            Map.entry("4", new Instrument("4", LocalDateTime.of(LocalDate.of(2000, 1, 1), LocalTime.of(1, 1, 1)), 400)),
            Map.entry("5", new Instrument("5", LocalDateTime.of(LocalDate.of(2000, 1, 1), LocalTime.of(1, 1, 1)), 500)),
            Map.entry("6", new Instrument("6", LocalDateTime.of(LocalDate.of(2000, 1, 1), LocalTime.of(1, 1, 1)), 600)),
            Map.entry("7", new Instrument("7", LocalDateTime.of(LocalDate.of(2000, 1, 1), LocalTime.of(1, 1, 1)), 700)),
            Map.entry("8", new Instrument("8", LocalDateTime.of(LocalDate.of(2000, 1, 1), LocalTime.of(1, 1, 1)), 800)),
            Map.entry("9", new Instrument("9", LocalDateTime.of(LocalDate.of(2000, 1, 1), LocalTime.of(1, 1, 1)), 900))
    );

    @BeforeAll
    static void generateMockData() {
        PriceDataTestGenerator.Pair pair = PriceDataTestGenerator.generateRandomPriceDataList(recordRandomNo);
        priceDataRandomList = pair.priceDataList();
        List<List<PriceData>> partitionedData = Lists.partition(priceDataRandomList, partitionSize);
        instrumentRandomMap = pair.instrumentMap();
        int counter = 0;
        for (List<PriceData> partitionedDatum : partitionedData) {
            priceDataChunkMap.put(counter, List.copyOf(partitionedDatum)); // Add partitioned list to WeakHashMap as a temporary repository. Deleted object will be garbage collected in the next GC cycle.
            counter++;
        }
    }

    @BeforeEach
    void startThread() throws IOException {
        //fill the storage with random data
        service.putAll(instrumentRandomMap);
        //start storage updater worker thread for updating database from queue
        ExecutorService threadPool = Executors.newFixedThreadPool(5);
        for (int i = 0; i < 5; i++) {
            threadPool.execute(service);
        }
    }

    @Test
    void startUploadComplete_concurrent() throws InterruptedException {
        log.info("#Storage: " + service.storageName() + " ;requestNo*Thread: " + requestNo*threadsNo+ " ;recordNo: " + recordRandomNo + " ;partitionSize: " + partitionSize + " ;service.size: " + service.size());
        var start = Instant.now().toEpochMilli();
        var threadPool = Executors.newFixedThreadPool(threadsNo);
        for (int j = 0; j < requestNo; j++) {
            var latchUser = new CountDownLatch(threadsNo);
            for (int i = 0; i < threadsNo; i++) {
                threadPool.execute(() -> {
                    try {
//                        splitStartUploadComplete(priceDataFixedList);/// //priceDataFixedList
                        splitStartUploadComplete(priceDataRandomList);/// //priceDataFixedList
                        latchUser.countDown();
                    } catch (Exception ignore) {
                        log.error("#Exception: " + ignore.getMessage());
                    }
                });
            }
            latchUser.await();
        }
        threadPool.shutdown();
    }

    private void splitStartUploadComplete(List<PriceData> batchListLocal) {
        var id0 = batchListLocal.get(0).id();
        var idLast = batchListLocal.get(batchListLocal.size() - 1).id();
        List<List<PriceData>> partitionedDataLocal = Lists.partition(batchListLocal, partitionSize);
        int counter = 0;
        Map<Integer, List<PriceData>> priceDataChunkMapLocal = new HashMap<>();
        for (List<PriceData> partitionedDatum : partitionedDataLocal) {
            priceDataChunkMapLocal.put(counter, List.copyOf(partitionedDatum)); // Add partitioned list to WeakHashMap as a temporary repository. Deleted object will be garbage collected in the next GC cycle.
            counter++;
        }

        var batchId = service.startBatchRun();
        for (Map.Entry<Integer, List<PriceData>> entry : priceDataChunkMapLocal.entrySet()) {
            List<PriceData> priceDataList = entry.getValue();
            service.uploadPriceData(batchId, priceDataList);
        }
        service.completeBatchRun(batchId);
        if (service.updateCounter(batchId) > 0)
            assertThat(service.updateCounter(batchId)).isEqualTo(recordRandomNo);

//        service.getLastPrice(id0);
//        assertEquals();
//        service.getLastPrice(idLast);
    }

    @Test
    void startUploadComplete() {
        var batchId = service.startBatchRun();
        for (Map.Entry<Integer, List<PriceData>> entry : priceDataChunkMap.entrySet()) {
            List<PriceData> priceDataList = entry.getValue();
            service.uploadPriceData(batchId, priceDataList);
        }
        service.completeBatchRun(batchId);

    }

    @Test
    void testResiliencyAgainstClient_startUploadComplete_whenGetLastPriceIsCalledDuringBatchCall() {
        List<PriceData> batchListLocal = PriceDataTestGenerator.generateRandomPriceDataList(8).priceDataList();
        var id0 = batchListLocal.get(0).id();
        var idLast = batchListLocal.get(batchListLocal.size() - 1).id();
        List<List<PriceData>> partitionedDataLocal = Lists.partition(batchListLocal, 3);
        int counter = 0;
        Map<Integer, List<PriceData>> priceDataChunkMapLocal = new HashMap<>();
        for (List<PriceData> partitionedDatum : partitionedDataLocal) {
            priceDataChunkMapLocal.put(counter, List.copyOf(partitionedDatum)); // Add partitioned list to WeakHashMap as a temporary repository. Deleted object will be garbage collected in the next GC cycle.
            counter++;
        }
        String batchId = service.startBatchRun();
        for (Map.Entry<Integer, List<PriceData>> entry : priceDataChunkMapLocal.entrySet()) {
            List<PriceData> priceDataList = entry.getValue();
            service.uploadPriceData(batchId, priceDataList);
        }
        RuntimeException thrown0 = Assertions.assertThrows(RuntimeException.class, () -> {
            service.getLastPrice(id0);
        });
        assertTrue(thrown0.getMessage().startsWith("#Instrument not found! id: "));
        RuntimeException thrownLast = Assertions.assertThrows(RuntimeException.class, () -> {
            service.getLastPrice(idLast);
        });
        assertTrue(thrownLast.getMessage().startsWith("#Instrument not found! id: "));

        service.completeBatchRun(batchId);
        assertEquals(service.getLastPrice(id0).id(), id0);
        assertEquals(service.getLastPrice(idLast).id(), idLast);
    }

    @Test
    void testResiliencyAgainstClient_startUploadCancel_whenGetLastPriceIsCalledDuringBatchCall() {
        List<PriceData> batchListLocal = PriceDataTestGenerator.generateRandomPriceDataList(8).priceDataList();
        List<List<PriceData>> partitionedDataLocal = Lists.partition(batchListLocal, 3);
        int counter = 0;
        Map<Integer, List<PriceData>> priceDataChunkMapLocal = new HashMap<>();
        for (List<PriceData> partitionedDatum : partitionedDataLocal) {
            priceDataChunkMapLocal.put(counter, List.copyOf(partitionedDatum)); // Add partitioned list to WeakHashMap as a temporary repository. Deleted object will be garbage collected in the next GC cycle.
            counter++;
        }
        String id0 = batchListLocal.get(0).id();
        String idLast = batchListLocal.get(batchListLocal.size() - 1).id();
        String batchId = service.startBatchRun();
        for (Map.Entry<Integer, List<PriceData>> entry : priceDataChunkMapLocal.entrySet()) {
            List<PriceData> priceDataList = entry.getValue();
            service.uploadPriceData(batchId, priceDataList);
        }
        RuntimeException thrown0 = Assertions.assertThrows(RuntimeException.class, () -> {
            service.getLastPrice(id0);
        });
        assertTrue(thrown0.getMessage().startsWith("#Instrument not found! id: "));
        RuntimeException thrownLast = Assertions.assertThrows(RuntimeException.class, () -> {
            service.getLastPrice(idLast);
        });
        assertTrue(thrownLast.getMessage().startsWith("#Instrument not found! id: "));

        service.cancelBatchRun(batchId);

        RuntimeException thrown01 = Assertions.assertThrows(RuntimeException.class, () -> {
            service.getLastPrice(id0);
        });
        assertTrue(thrown01.getMessage().startsWith("#Instrument not found! id: "));
        RuntimeException thrownLast1 = Assertions.assertThrows(RuntimeException.class, () -> {
            service.getLastPrice(idLast);
        });
        assertTrue(thrownLast1.getMessage().startsWith("#Instrument not found! id:  "));
    }

    @Test
    void testResilienceAgainstOrder_startUpload_shouldThrowError_whenResilienceNotMeet() {//Todo Change the name
        String batchId = service.startBatchRun();
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class, () -> {
            service.uploadPriceData(UUID.randomUUID().toString(), priceDataRandomList);
        });
        assertTrue(thrown.getMessage().startsWith("Illegal state in uploadPriceData is detected! batchId: "));
        service.cancelBatchRun(batchId);
    }

    @Test
    void shouldReturnUUID_whenStartBatchRunIsCalled() {
        String batchId = service.startBatchRun();
        assertNotNull(batchId);
    }

    //    @Test
    void uploadPriceData() {
    }


    @Test
    void completeBatchRun() {
        String batchId = service.startBatchRun();
        service.completeBatchRun(batchId);
    }

    @Test
    void completeBatchRunWithoutStartIsCalled() {
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class, () -> {
            service.completeBatchRun(UUID.randomUUID().toString());
        });
        assertTrue(thrown.getMessage().startsWith("Illegal state in completeBatchRun! batchId:"));
    }

    @Test
    void cancelBatchRun() {
        String batchId = service.startBatchRun();
        service.cancelBatchRun(batchId);
    }

    @Test
    void cancelBatchRunWithoutStart() {
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class, () -> {
            service.cancelBatchRun(UUID.randomUUID().toString());
        });
        assertTrue(thrown.getMessage().startsWith("Illegal state in cancelBatchRun! batchId: "));
    }

    //    @Test
    void getLastPrice() {
    }

    public static void main(String[] args) {
        try {
            List<GarbageCollectorMXBean> gcMxBeans = ManagementFactory.getGarbageCollectorMXBeans();

            for (GarbageCollectorMXBean gcMxBean : gcMxBeans) {
                System.out.println(gcMxBean.getName());
                System.out.println(gcMxBean.getObjectName());
            }

        } catch (RuntimeException re) {
            throw re;
        } catch (Exception exp) {
            throw new RuntimeException(exp);
        }
    }
}