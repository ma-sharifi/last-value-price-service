//package com.example.service;
//
//import com.example.exception.NotFoundException;
//import com.example.model.Instrument;
//import com.example.model.PriceData;
//import com.example.storage.StorageService;
//import com.example.storage.annotation.Storage;
//import com.github.benmanes.caffeine.cache.Cache;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.context.annotation.Primary;
//import org.springframework.stereotype.Service;
//
//import java.lang.ref.WeakReference;
//import java.util.List;
//import java.util.Map;
//import java.util.Objects;
//import java.util.UUID;
//import java.util.concurrent.BlockingQueue;
//import java.util.concurrent.LinkedBlockingDeque;
//import java.util.concurrent.atomic.AtomicInteger;
//import java.util.concurrent.locks.ReentrantLock;
//
///**
// * @author Mahdi Sharifi
// */
//@Service
//@Primary
//@Slf4j
//public class InMemoryPriceTrackingServiceBK implements PriceTrackingService {
////    private static final Map<String, BlockingQueue<PriceData>> PRICE_DATA_BUFFER_MAP = new MapMaker().weakKeys().makeMap(); //Change to ThreadLocal?
//
//    @Autowired
//    private Cache<String, BlockingQueue<PriceData>> PRICE_DATA_BUFFER_MAP; // It has weak reference and soft value it can remove un consumed batchId after specified time
//
//    @Autowired
//    private Cache<String, Instrument> instrumenCache;//Latest updated instrument data are here //TODO Expire time must be less than specific time for eviction
//
//    private static final BlockingQueue<WeakReference<Instrument>> INSTRUMNET_STORAGE_UPDATER_BQUEUE = new LinkedBlockingDeque<>();
//
//    private static final AtomicInteger UPDATE_COUNTER_NEEDED = new AtomicInteger();
//
//    private final StorageService<String, Instrument> instrumentStorage;
//
//    ReentrantLock lock = new ReentrantLock();
//
//    public InMemoryPriceTrackingServiceBK(@Storage(Storage.Type.MAP) StorageService<String, Instrument> instrumentStorage) {
//        this.instrumentStorage = instrumentStorage;
//        // if you want to have more storage updater worker, you can start a new thread. As a default I use one storage updater thread.
//        Thread storageUpdaterThread= new Thread(this); //Start storage updater thread
//        storageUpdaterThread.start();
//    }
//
//    @Override
//    public String startBatchRun() {
//        var batchId = UUID.randomUUID().toString();
//        PRICE_DATA_BUFFER_MAP.put(batchId, new LinkedBlockingDeque<>());
//        return batchId;
//    }
//
//    @Override
//    public void uploadPriceData(String batchId, List<PriceData> priceDataList) {//StartBatch-> Chunk[1..1000],Chunk[1001..2000],Chunk[2001]->Complete/Cancel
//        if (PRICE_DATA_BUFFER_MAP.getIfPresent(batchId) == null)
//            throw new IllegalStateException("Illegal state in uploadPriceData is detected! You should start a batch at first! batchId: " + batchId);
//        Objects.requireNonNull(PRICE_DATA_BUFFER_MAP.getIfPresent(batchId)).addAll(priceDataList);
//    }
//
//    @Override
//    public synchronized void completeBatchRun(String batchId) {
//        if (PRICE_DATA_BUFFER_MAP.getIfPresent(batchId) == null)
//            throw new IllegalStateException("Illegal state in completeBatchRun is detected! You should start a batch at first!  batchId: " + batchId);
//
//        var queueOneBatch = PRICE_DATA_BUFFER_MAP.getIfPresent(batchId);
//        for (PriceData priceData : queueOneBatch) {
//            var instrument = getLastPrice(priceData.id());// Read instrument from cache or database. It can be replaced with redis
//            if (priceData.asOf().isAfter(instrument.updatedAt())) {
//                var instrumentUpdated = new Instrument(priceData.id(), priceData.asOf(), priceData.payload());//create new instrument from old instrument and new prieData
//                updateStorageAndCache(instrumentUpdated);
//            }
//        }
//
//        PRICE_DATA_BUFFER_MAP.invalidate(batchId);//remove the batch id, it means remove all data about these chunk of records.
//    }
//
//    private void updateStorageAndCache(Instrument instrumentUpdated) {
//        //Update current instrument specified by priceData.id
//        WeakReference<Instrument> weakInstrument = new WeakReference<>(instrumentUpdated);
//        INSTRUMNET_STORAGE_UPDATER_BQUEUE.add(weakInstrument);//database need to be updated
//        instrumenCache.put(instrumentUpdated.id(), instrumentUpdated);//Update cache
//        UPDATE_COUNTER_NEEDED.getAndIncrement();
//    }
//
//    @Override
//    public synchronized Instrument getLastPrice(String instrumentId) throws RuntimeException {
//        //Read instrument from cache not database, because maybe data are still in the queue not database
//        var instrumentInCache = instrumenCache.getIfPresent(instrumentId);//Because the change maybe not be effected into storage yet, because  it is still in the queue
//        if (instrumentInCache != null) return instrumentInCache;
//
//        //If latest data does not exist in cache, we read it from storage
//        var instrumentInStorage = instrumentStorage.getIfPresent(instrumentId);
//        if (instrumentInStorage == null) throw new NotFoundException("#Instrument not found! id: " + instrumentId);
//        return instrumentInStorage;
//    }
//
//    @Override
//    public void cancelBatchRun(String batchId) {
//        if (PRICE_DATA_BUFFER_MAP.getIfPresent(batchId) == null)
//            throw new IllegalStateException("Illegal state in cancelBatchRun! You should start a batch at first!  batchId: " + batchId);
//        PRICE_DATA_BUFFER_MAP.invalidate(batchId);
//    }
//
//    //I don't update storage directly when batch is completed, because storage is slow!
//    //I put instrument need to be updated into INSTRUMNET_UPDATE_BUFFER BlockingQueue
//    //Consuming instrument from INSTRUMNET_UPDATE_BUFFER and update the storage
//    //I don't update storage directly by complete bach! We put instrument need to be updated into INSTRUMNET_UPDATE_BUFFER BlockingQueue
//    //Consuming instrument from INSTRUMNET_UPDATE_BUFFER and update the database
//    @Override
//    public void run() {
//         log.info("#Updater worker is started. Thread name: " + Thread.currentThread().getName());
//        try {
//            while (true) {
//                var weakInstrument = INSTRUMNET_STORAGE_UPDATER_BQUEUE.take(); // Blocking call - waits until an element is available
//                Instrument instrument = Instrument.of(Objects.requireNonNull(weakInstrument.get()));
//                instrumentStorage.put(instrument.id(), instrument);
//            }
//        } catch (InterruptedException exception) {
//            log.info("#InterruptedException reason: " + exception.getMessage());
//            Thread.currentThread().interrupt();
//        }
//    }
//
//    @Override
//    public synchronized String storageName() {
//        return instrumentStorage.name();
//    }
//
//    @Override
//    public synchronized void putAll(Map<String, Instrument> instrumentMap) {
//        instrumentStorage.putAll(instrumentMap);
//    }
//
//    @Override
//    public synchronized long size() {
//        return instrumentStorage.size();
//    }
//
//    @Override
//    public synchronized void print() {
//        instrumentStorage.print();
//    }
//
//    @Override
//    public synchronized Map<String, Instrument> getAll() {
//        return instrumentStorage.getAll();
//    }
//
//
//    @Override
//    public int readCounter() {
//        return UPDATE_COUNTER_NEEDED.get();
//    }
//}
