package com.example.service;

import com.example.exception.NotFoundException;
import com.example.model.Instrument;
import com.example.model.PriceData;
import com.example.storage.StorageService;
import com.example.storage.annotation.Storage;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Mahdi Sharifi
 */
@Service
@Slf4j
public class InMemoryPriceTrackingService implements PriceTrackingService {

    public static final int UPDATER_WORKER_THREAD_NO = 5;//Number of updater worker threads. This number is obtained from the load test

    @Autowired
    private Cache<String, BlockingQueue<PriceData>> PRICE_DATA_TO_BQUEUE_CACHE; // It holds batch data, key is batchId. It can remove unconsumed batchId after specified time

    @Autowired
    private Cache<String, Instrument> instrumenCache;//The latest-updated instrument data holds into this cache

    @Autowired
    private Cache<String, AtomicInteger> UPDATE_COUNTER_MAP;//Keep how many instruments are updated by this batch. Key is batchId

    private static final BlockingQueue<WeakReference<Instrument>> INSTRUMNET_STORAGE_UPDATER_BQUEUE = new LinkedBlockingDeque<>();
    //This service provides storage/database operation for us
    private final StorageService<String, Instrument> instrumentStorage;

    public InMemoryPriceTrackingService(@Storage(Storage.Type.MAP) StorageService<String, Instrument> instrumentStorage) {
        this.instrumentStorage = instrumentStorage;
       for(int i = 0; i< UPDATER_WORKER_THREAD_NO; i++) {  //if you want to have more storage updater worker, you can start a new thread. As a default 5 worker is started.
           Thread storageUpdaterThread = new Thread(this); //create storage updater thread
           storageUpdaterThread.start();//Start storage updater thread
       }
    }

    /**
     * Generate a batchId and return it as a response to producer. It is used for 2 purposes:
     * For resiliency: Protect the service against producers which call the service methods in an incorrect order
     * For holding chunk data as a batch in a Map
     * @return batchId
     */
    @Override
    public String startBatchRun() {
        var batchId = UUID.randomUUID().toString();
        PRICE_DATA_TO_BQUEUE_CACHE.put(batchId,new LinkedBlockingDeque<>()); //initialize a batch
        UPDATE_COUNTER_MAP.put(batchId, new AtomicInteger(0)); //initialize counter
        return batchId;
    }

    /**
     * Consume batchId and chunk of priceData and put the data into BlockingQueue.
     * With batchId it gets the blockingQueue of this batch then adds this chunk into its own BlockingQueue. It also protects the client from unCompleted/Canceled data.
     * @param batchId Specifies the batch
     * @param priceDataList a chunk of priceData
     */
    @Override
    public void uploadPriceData(String batchId, List<PriceData> priceDataList) {
        if (PRICE_DATA_TO_BQUEUE_CACHE.getIfPresent(batchId) == null)
            throw new IllegalStateException("Illegal state in uploadPriceData is detected! You should start a batch at first! batchId: " + batchId);
        assert PRICE_DATA_TO_BQUEUE_CACHE.getIfPresent(batchId) != null;
        PRICE_DATA_TO_BQUEUE_CACHE.getIfPresent(batchId).addAll(priceDataList);
    }

    /**
     * The Producer calls this method to tell the service the batch is finished and start processing the batch.
     * If priceData is after instrumnet, it put a copy of the instrument into BlockingQueue and Cache.
     * @param batchId Specifies the batch
     */
    @Override
    public synchronized void completeBatchRun(String batchId) {
        if (PRICE_DATA_TO_BQUEUE_CACHE.getIfPresent(batchId) == null)
            throw new IllegalStateException("Illegal state in completeBatchRun is detected! You should start a batch at first!  batchId: " + batchId);

        BlockingQueue<PriceData> queueOneBatch = PRICE_DATA_TO_BQUEUE_CACHE.getIfPresent(batchId);
        assert queueOneBatch != null;
        queueOneBatch.forEach(priceData -> {
                    var instrument = getLastPrice(priceData.id());// Read an instrument from cache or storage/database
                    if (priceData.asOf().isAfter(instrument.updatedAt())) {
                        var instrumentUpdated = new Instrument(priceData.id(), priceData.asOf(), priceData.payload());//TODO (Check document: solution) create new instrument from old instrument and new prieData
                        updateStorageAndCache(batchId, instrumentUpdated);
                    }
                });
        PRICE_DATA_TO_BQUEUE_CACHE.invalidate(batchId);//remove the batch id (remove the batch), it means remove all data about these chunk of records.
    }

    /**
     * The Producer calls this method to tell the service the batch is canceled. Don't process it. Just ignore it.
     * @param batchId Specifies the batch
     */
    @Override
    public void cancelBatchRun(String batchId) {
        if (PRICE_DATA_TO_BQUEUE_CACHE.getIfPresent(batchId) == null)
            throw new IllegalStateException("Illegal state in cancelBatchRun! You should start a batch at first!  batchId: " + batchId);
        PRICE_DATA_TO_BQUEUE_CACHE.invalidate(batchId);
    }

    private void updateStorageAndCache(String batchId, Instrument instrumentUpdated) {
        //Update a current instrument specified by priceData.id
        WeakReference<Instrument> weakInstrument = new WeakReference<>(instrumentUpdated);
        INSTRUMNET_STORAGE_UPDATER_BQUEUE.add(weakInstrument);//database need to be updated
        instrumenCache.put(instrumentUpdated.id(), instrumentUpdated);//Update cache
        UPDATE_COUNTER_MAP.getIfPresent(batchId).getAndIncrement(); //increment update counter of the batch
    }

    /**
     * Consumer can't see the data of the batch as are receiving and before complete the batch.
     * After that batch is completed by call Complete method with batchId, the data will be visible for consumer.
     * Read an instrument from the cache at first not the storage/database, because maybe data are still in the queue not storage/database
     * Because the change maybe not be effected into storage yet, because it is still in the queue since storage is slow.
     * @param instrumentId
     * @return instrument from cache of storage/database
     * @throws RuntimeException
     */
    @Override
    public Instrument getLastPrice(String instrumentId) throws RuntimeException {
        var instrumentInCache = instrumenCache.getIfPresent(instrumentId);
        if (instrumentInCache != null) return instrumentInCache;
        //If latest data does not exist in cache, then read it from storage
        var instrumentInStorage = instrumentStorage.get(instrumentId);
        if (instrumentInStorage == null) throw new NotFoundException("#Instrument not found! id: " + instrumentId);
        return instrumentInStorage;
    }

    /**
     * I don't update storage directly when batch is completed, because storage is slow!
     * We know storage(database) is much slower than cache. Instead of updating the database directly when batch is completed,
     * we put the data need to be updated into shared BlockingQueue in the complete batch section. Multi thread can
     * consume this BlockingQueue. The Service starts five threads by default. User can start as many threads as required.
     */
    @Override
    public void run() {
        log.info("#Updater worker is started. Thread name: " + Thread.currentThread().getName());
        try {
            while (true) {
                var weakInstrument = INSTRUMNET_STORAGE_UPDATER_BQUEUE.take(); // Blocking call - waits until an element is available
                Instrument instrument = Instrument.of(Objects.requireNonNull(weakInstrument.get()));
                instrumentStorage.put(instrument.id(), instrument);
            }
        } catch (InterruptedException exception) {
            log.info("#InterruptedException reason: " + exception.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String storageName() {
        return instrumentStorage.name();
    }

    @Override
    public synchronized void putAll(Map<String, Instrument> instrumentMap) {
        instrumentStorage.putAll(instrumentMap);
    }

    @Override
    public synchronized long size() {
        return instrumentStorage.size();
    }

    @Override
    public synchronized void print() {
        instrumentStorage.print();
    }

    @Override
    public synchronized Map<String, Instrument> getAll() {
        return instrumentStorage.getAll();
    }


    @Override
    public int updateCounter(String batchId) {
        AtomicInteger counter = UPDATE_COUNTER_MAP.getIfPresent(batchId);
        if (counter != null) return counter.get();
        return -1;
    }
}
