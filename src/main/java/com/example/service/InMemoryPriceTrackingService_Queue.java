//package com.example.service;
//
//import com.example.model.Instrument;
//import com.example.model.PriceData;
//import com.example.storage.StorageService;
//import com.example.storage.annotation.Storage;
//import com.google.common.collect.MapMaker;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.context.annotation.Primary;
//import org.springframework.stereotype.Service;
//
//import java.lang.ref.WeakReference;
//import java.time.LocalDateTime;
//import java.util.List;
//import java.util.Map;
//import java.util.Objects;
//import java.util.UUID;
//import java.util.concurrent.BlockingQueue;
//import java.util.concurrent.LinkedBlockingDeque;
//import java.util.concurrent.atomic.AtomicInteger;
//import java.util.concurrent.locks.*;
//
///**
// * @author Mahdi Sharifi
// */
////@Service
////@Primary
//@Slf4j
//public class InMemoryPriceTrackingService_Queue implements PriceTrackingService {
//
//    private static final Map<String, BlockingQueue<PriceData>> PRICE_DATA_BUFFER_MAP = new MapMaker().weakKeys().makeMap(); //Change to ThreadLocal?
//    private static final BlockingQueue<WeakReference<Instrument>> INSTRUMNET_UPDATE_BUFFER = new LinkedBlockingDeque<>();
//    private static final AtomicInteger UPDATE_COUNTER = new AtomicInteger();
//
//    private final StorageService<String, Instrument> instrumentStorage;
//
//    public InMemoryPriceTrackingService_Queue(@Storage(Storage.Type.MAP) StorageService<String, Instrument> instrumentStorage) {
//        this.instrumentStorage = instrumentStorage;
//    }
//
//    @Override
//    public String startBatchRun() {
//        String stateId = UUID.randomUUID().toString();
//        PRICE_DATA_BUFFER_MAP.put(stateId, new LinkedBlockingDeque<>());
//        return stateId;
//    }
//
//    @Override
//    public void uploadPriceData(List<PriceData> priceDataList, String stateId) {//StartBatch-> Chunk[1..1000],Chunk[1001..2000],Chunk[2001]->Complete/Cancel
//        if (!PRICE_DATA_BUFFER_MAP.containsKey(stateId))
//            throw new IllegalStateException("Illegal state in uploadPriceData is detected! stateId: " + stateId);
//        BlockingQueue<PriceData> queueOneBatch = PRICE_DATA_BUFFER_MAP.get(stateId);
//        queueOneBatch.addAll(priceDataList);
//    }
//
//    // On completion, all prices in a batch run should be made available at the same time.
//    @Override
//    public void completeBatchRun(String stateId) {
//        r.lock();
//
//        try {
//            if (!PRICE_DATA_BUFFER_MAP.containsKey(stateId))
//                throw new IllegalStateException("Illegal state in completeBatchRun! stateId: " + stateId);
//            BlockingQueue<PriceData> queueOneBatch = PRICE_DATA_BUFFER_MAP.get(stateId);
//            for (PriceData priceData : queueOneBatch) {
//                Instrument instrumentSaved = null;
////                try {
//                instrumentSaved = instrumentStorage.getIfPresent(priceData.id());
////                }finally {
////                    r.unlock();
////                }
//                if (instrumentSaved != null) {
//                    if (priceData.asOf().isAfter(instrumentSaved.getUpdatedAt())) {
//                        instrumentSaved.setPrice(priceData.payload());
//                        instrumentSaved.setUpdatedAt(priceData.asOf());
//                        WeakReference<Instrument> weakInstrument = new WeakReference<>(instrumentSaved);
//                        INSTRUMNET_UPDATE_BUFFER.add(weakInstrument);
//                        UPDATE_COUNTER.getAndIncrement();
//                    }
//                }
//            }
//        } finally {
////            stampedLock.unlockRead(stamp);
//        }
//        r.unlock();
//        PRICE_DATA_BUFFER_MAP.remove(stateId);
////        log.info("#completeBatchRun-> stateId = " + stateId+ " ;queueOneBatch.size: "+queueOneBatch.size()+" Transferred! "+" ;buffer.size: "+ PRICE_DATA_BUFFER_MAP.size()+" ;Storage.size: "+instrumentStorage.size());
//    }
//
//    //    Batch runs which are cancelled can be discarded.
//    @Override
//    public void cancelBatchRun(String stateId) {
//        if (!PRICE_DATA_BUFFER_MAP.containsKey(stateId))
//            throw new IllegalStateException("Illegal state in cancelBatchRun! stateId: " + stateId);
//        PRICE_DATA_BUFFER_MAP.remove(stateId);
////        log.info("#cancelBatchRun-> stateId = " + stateId+ " ;buffer.size: "+ PRICE_DATA_BUFFER_MAP.size()+" Transfered! "+" ;MAP. new size: "+instrumentStorage.size());
//    }
//
//    //The last value is determined by the asOf time, as set by the producer.
//    //is it possible to change the last price does not exist in last batch? and we need to search for it?
//    @Override
//    public Instrument getLastPrice(String priceId) throws RuntimeException {
//        Instrument instrument = instrumentStorage.getIfPresent(priceId);
//        if (instrument == null) throw new RuntimeException("#Instrument not found! id: " + priceId);
//        return instrument;
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
//    //    ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
//    ReentrantLock reentrantLock = new ReentrantLock();
//    private final StampedLock stampedLock = new StampedLock();
//    ReadWriteLock rw = new ReentrantReadWriteLock();
//    private final Lock r = rw.readLock();
//    private final Lock w = rw.writeLock();
//
//    //I don't update storage directly by complete bach! We put instrument need to be updated into INSTRUMNET_UPDATE_BUFFER BlockingQueue
//    //Consuming instrument from INSTRUMNET_UPDATE_BUFFER and update the database
//    @Override
//    public void run() {
//        try {
//            while (true) {
//                var weakInstrument = INSTRUMNET_UPDATE_BUFFER.take(); // Blocking call - waits until an element is available
//                Instrument instrument = Instrument.of(Objects.requireNonNull(weakInstrument.get()));
////                reentrantLock.lock();
////                long stamp=stampedLock.writeLock();
//                try {
//                    w.lock();
//                    instrumentStorage.put(instrument.getId(), instrument);
//                    w.unlock();
//                } finally {
////                    stampedLock.unlockWrite(stamp); // Release the write lock
////                    reentrantLock.unlock();
//                }
//                UPDATE_COUNTER.getAndDecrement();
//                log.info("#Updating database..." + instrument + " ;updateCounter: " + UPDATE_COUNTER.get());
//
//            }
//        } catch (InterruptedException exception) {
//            log.info("#InterruptedException reason: " + exception.getMessage());
//            Thread.currentThread().interrupt();
//        }
//    }
//}
