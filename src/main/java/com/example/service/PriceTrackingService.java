package com.example.service;

import com.example.model.Instrument;
import com.example.model.PriceData;
import com.github.benmanes.caffeine.cache.Cache;

import java.util.List;
import java.util.Map;

/**
 * @author Mahdi Sharifi
 * designd and implemented a service for keeping track of the last price for financial instruments.
 * Producers will use the service to publish prices and consumers will use it to obtain them.
 * The service is resilient against producers which call the service methods in an incorrect order,
 * or clients which call the service while a batch is being processed.
 */
public interface PriceTrackingService extends Runnable {
    String startBatchRun();

    void uploadPriceData(String batchId, List<PriceData> priceDataList) throws IllegalStateException;

    void completeBatchRun(String batchId) throws IllegalStateException;

    void cancelBatchRun(String batchId) throws IllegalStateException;

    Instrument getLastPrice(String instrumentId) throws RuntimeException;

    String storageName();

    void putAll(List<Instrument> instrumentList);

    long size();

    void print();

    Map<String, Instrument> getAll();

    int updateCounter(String batchId);

    Cache<String, Instrument> getInstrumenCache();
}
