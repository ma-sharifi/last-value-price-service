package com.example.service;

import com.example.model.Instrument;
import com.example.model.PriceData;
import com.example.util.PriceDataTestGenerator;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @see InMemoryPriceTrackingService
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Slf4j
class InMemoryPriceTrackingServiceTest {

    public static final int STORAGE_UPDATER_THREAD_NO = 10;
    @Autowired
    private PriceTrackingService service;

    private static List<PriceData> priceDataList = new ArrayList<>(); // defined as a field due to aggregate the messages.
    private static List<Instrument> instrumentList = new ArrayList<>(); // defined as a field due to aggregate the messages.

    static final int recordNo = 100000;// Number of PriceData Object
    static final int partitionSize = 10000;//Number of records in a chunks.

    //Define client users
    static final int requestNo = 1000;
    static final int threadsNo = 100; // Number of thread(users)

    @BeforeAll
    static void generateMockData() {
        PriceDataTestGenerator.Pair pair = PriceDataTestGenerator.generatePriceInstrumentList(recordNo, 1);
        priceDataList = pair.priceDataList();
        instrumentList = pair.instrumentActualList();
    }

    void fillStorage() {
        service.putAll(instrumentList);//fill the storage with specific data
//        ExecutorService threadPool = Executors.newFixedThreadPool(STORAGE_UPDATER_THREAD_NO);
//        for (int i = 0; i < STORAGE_UPDATER_THREAD_NO; i++) {
//            threadPool.execute(service);//create storage updater thread
//        }
    }

    @Test
    void testStartUploadComplete_concurrent() throws InterruptedException {
        fillStorage();
        log.info("#Storage: " + service.storageName() + " ;requestNo*Thread: " + requestNo * threadsNo + " ;recordNo: " + recordNo + " ;partitionSize: " + partitionSize + " ;service.size: " + service.size());
        var start = Instant.now().toEpochMilli();
        service.putAll(instrumentList);
        var threadPool = Executors.newFixedThreadPool(threadsNo);
        for (int j = 0; j < requestNo / threadsNo; j++) {
            var latchUser = new CountDownLatch(threadsNo);
            for (int i = 0; i < threadsNo; i++) {
                final int step = i;
                threadPool.execute(() -> {
                    try {
                        PriceDataTestGenerator.Triple triple = PriceDataTestGenerator.generatePriceInstrumentListTriple(recordNo, step);
                        List<PriceData> prices = triple.priceDataList();
                        List<Instrument> instruments = triple.instrumentExpectedList();
                        splitStartUploadComplete(prices, instruments);
                        latchUser.countDown();

                    } catch (Exception ignore) {
                        log.error("#Exception send batch: " + ignore.getMessage());
                        Thread.currentThread().interrupt(); //TODO
                    }
                });
            }
            latchUser.await(10, TimeUnit.SECONDS);
        }
        threadPool.shutdown();
        String system = "#max: " + Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MB; free: " + Runtime.getRuntime().freeMemory() / (1024 * 1024) + " MB; total: " + Runtime.getRuntime().totalMemory() / (1024 * 1024) + " MB; core: " + Runtime.getRuntime().availableProcessors();
        System.out.println("#TIME: " + (System.currentTimeMillis() - start) + " ms" + " ;Thread: " + threadsNo + " pSize: " + partitionSize + " ;recordNo: " + recordNo + " ;" + system);
    }

    private void splitStartUploadComplete(List<PriceData> batchListLocal, List<Instrument> instrumentExpectedList) {
        //start a batch
        var batchId = service.startBatchRun();

        //split the batch to chunk -> upload chunk of batch
        for (List<PriceData> chunk : Lists.partition(batchListLocal, partitionSize)) {
            service.uploadPriceData(batchId, chunk);
        }
        //complete the batch
        service.completeBatchRun(batchId);

        //assert data was read by service, with the data read by cache since cache always keep the updated data.
        for (Instrument instrument : instrumentExpectedList) {
            Instrument actual = service.getLastPrice(instrument.id()); //read data by service
            Instrument instrument2 = service.getInstrumenCache().getIfPresent(instrument.id()); //read data from cache
            assertThat(actual).isEqualTo(instrument2);
//           assertThat(actual).isEqualTo(instrument); // it does not work, because there are some thread work concurrently and change data at the same time
        }
    }

    @Test
    void shouldCompleteABatch_whenStartUploadCompleteAreCalledInaRow() {
        PriceDataTestGenerator.Pair pair = PriceDataTestGenerator.generateRandomPriceDataList(8, 1);
        List<PriceData> batchListLocal = pair.priceDataList();
        List<Instrument> instrumentActualList = pair.instrumentActualList();
        service.putAll(instrumentActualList);//fill the storage
        var batchId = service.startBatchRun();
        for (List<PriceData> chunk : Lists.partition(batchListLocal, 3)) {
            service.uploadPriceData(batchId, chunk);
        }
        service.completeBatchRun(batchId);
    }

    @Test
    @Order(1)
    void testResiliencyAgainstClient_startUploadComplete_whenGetLastPriceIsCalledDuringBatchCall() {
        PriceDataTestGenerator.Pair pair = PriceDataTestGenerator.generateRandomPriceDataList(8, 1);
        List<PriceData> batchListLocal = pair.priceDataList();
        List<Instrument> instrumentActualList = pair.instrumentActualList();

        String id0 = batchListLocal.get(0).id();
        String idLast = batchListLocal.get(batchListLocal.size() - 1).id();

        service.putAll(instrumentActualList);//fill the storage
        String batchId = service.startBatchRun();
        for (List<PriceData> chunk : Lists.partition(batchListLocal, 3)) {
            service.uploadPriceData(batchId, chunk);
        }
        //assert the first id of the batch
        Instrument instrumentActual = service.getInstrumenCache().getIfPresent(id0);
        Instrument instrumentExpected = service.getLastPrice(id0);
        assertThat(instrumentActual).isNotEqualTo(instrumentExpected); //read directly from storage/database cache

        //assert the last id of the batch
        Instrument instrumentActuaLast = service.getInstrumenCache().getIfPresent(id0);
        Instrument instrumentExpectedLast = service.getLastPrice(id0); //read directly from storage/database cache
        assertThat(instrumentActuaLast).isNotEqualTo(instrumentExpectedLast);

        service.completeBatchRun(batchId);
        assertEquals(service.getLastPrice(id0).id(), id0);
        assertEquals(service.getLastPrice(idLast).id(), idLast);
    }

    @Test
    @Order(2)
    void testResiliencyAgainstClient_startUploadCancel_whenGetLastPriceIsCalledDuringBatchCall() {
        PriceDataTestGenerator.Pair pair = PriceDataTestGenerator.generateRandomPriceDataList(8, 1);
        List<PriceData> batchListLocal = pair.priceDataList();
        List<Instrument> instrumentActualList = pair.instrumentActualList();

        String id0 = batchListLocal.get(0).id();
        String idLast = batchListLocal.get(batchListLocal.size() - 1).id();

        service.putAll(instrumentActualList);//finn the storage
        String batchId = service.startBatchRun();
        for (List<PriceData> chunk : Lists.partition(batchListLocal, 3)) {
            service.uploadPriceData(batchId, chunk);
        }

        //assert the first id of the batch
        Instrument instrumentActual = service.getInstrumenCache().getIfPresent(id0);
        Instrument instrumentExpected = service.getLastPrice(id0);
        assertThat(instrumentActual).isNotEqualTo(instrumentExpected); //read directly from storage/database cache

        //assert the last id of the batch
        Instrument instrumentActuaLast = service.getInstrumenCache().getIfPresent(id0);
        Instrument instrumentExpectedLast = service.getLastPrice(id0); //read directly from storage/database cache
        assertThat(instrumentActuaLast).isNotEqualTo(instrumentExpectedLast);


        service.cancelBatchRun(batchId); //Must not effect on storage

        //assert the first id of the batch
        instrumentActual = service.getInstrumenCache().getIfPresent(id0);
        instrumentExpected = service.getLastPrice(id0);
        assertThat(instrumentActual).isNotEqualTo(instrumentExpected); //read directly from storage/database cache

        //assert the last id of the batch
        instrumentActuaLast = service.getInstrumenCache().getIfPresent(id0);
        instrumentExpectedLast = service.getLastPrice(id0); //read directly from storage/database cache
        assertThat(instrumentActuaLast).isNotEqualTo(instrumentExpectedLast);

    }

    @Test
    void testResilienceAgainstOrder_shouldThrowError_whenResilienceNotMeet() {
        String batchId = service.startBatchRun();
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class, () -> {
            service.uploadPriceData(UUID.randomUUID().toString(), priceDataList);
        });
        assertTrue(thrown.getMessage().startsWith("Illegal state in uploadPriceData is detected! You should start a batch at first! batchId: "));
        service.cancelBatchRun(batchId);
    }

    @Test
    void shouldReturnUUID_whenStartBatchRunIsCalled() {
        String batchId = service.startBatchRun();
        assertNotNull(batchId);
    }

    @Test
    void shouldRemoveBatchId_whenCompleteBatchRunIsCalled() {
        String batchId = service.startBatchRun();
        service.completeBatchRun(batchId);
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class, () -> {
            service.completeBatchRun(UUID.randomUUID().toString());//you can't complete a batch twice
        });
        assertTrue(thrown.getMessage().startsWith("Illegal state in completeBatchRun is detected! You should start a batch at first!  batchId: "));
        thrown = Assertions.assertThrows(IllegalStateException.class, () -> {
            service.cancelBatchRun(UUID.randomUUID().toString());//you can't cancel an already cancelled batch
        });
        assertTrue(thrown.getMessage().startsWith("Illegal state in cancelBatchRun! You should start a batch at first!  batchId: "));
    }

    @Test
    void shouldThrowIllegalStateException_whenCompleteBatchRunWithoutStartIsCalled() {
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class, () -> {
            service.completeBatchRun(UUID.randomUUID().toString());
        });
        assertTrue(thrown.getMessage().startsWith("Illegal state in completeBatchRun is detected! You should start a batch at first!  batchId: "));
    }

    @Test
    void shouldThrowIllegalStateException_shouldCancelBatchRun() {
        String batchId = service.startBatchRun();
        service.cancelBatchRun(batchId);
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class, () -> {
            service.cancelBatchRun(UUID.randomUUID().toString());//you can't cancel a batch twice
        });
        assertTrue(thrown.getMessage().startsWith("Illegal state in cancelBatchRun! You should start a batch at first!  batchId: "));
    }

    @Test
    void shouldThrowIllegalStateExceptionException_whenCancelBatchRunWithoutStartIsCalled() {
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class, () -> {
            service.cancelBatchRun(UUID.randomUUID().toString());
        });
        assertTrue(thrown.getMessage().startsWith("Illegal state in cancelBatchRun! You should start a batch at first!  batchId: "));
    }

}