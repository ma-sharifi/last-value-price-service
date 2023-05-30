package com.example.config;

import com.example.model.Instrument;
import com.example.model.PriceData;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.ref.WeakReference;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Mahdi Sharifi
 * This cache used for handling short live objects for queue and cached data
 * The constant numbers here should set from statistics data that is extracted from production environemnt
 */
@Configuration
public class CaffeineCacheConfig {

    /**
     * This is the main configuration that will control caching behavior such as expiration, cache size limits, and more
     * The WeakReference usage allows garbage-collection of objects when there are not any strong references to the object.
     * One BlockingQueue per batch
     * NOTE: The data of this cache also remove manually from cache.
     */
    @Bean
    public Cache<String, BlockingQueue<PriceData>> priceDataCaffeineConfig() {
        return Caffeine
                .newBuilder()
                .initialCapacity(10_000)
                .softValues()//Cache to use soft references for values.
                .expireAfterAccess(10, TimeUnit.MINUTES) // Expires after 10 minute since the last access. The all data of batch will be removed after this time. Takes precedence over the expireAfterWrite
                .build();
    }

    /**
     * Used next to Updater BlockingQueue to hold which data is enqueued. It holds updated storage data.
     * When a data touch in storage with getLastPrice method, data will store in this cache. Actually it's the cache of our database.
     * If we have more memory we can increase how long the data will last here.
     * NOTE: The data of this cache does not expire manually.
     */
    @Bean
    public Cache<String, Instrument> instrumentCaffeineConfig() {
        return Caffeine
                .newBuilder()
                .initialCapacity(10_000)
                .softValues()//Evict when the garbage collector needs to free memory
                .expireAfterAccess(1, TimeUnit.DAYS) //
                .build();
    }

    /**
     * Keeping how many update we had in a batch specified by batchId, it is used for metrics.
     * One counter per batch
     */
    @Bean
    public Cache<String, AtomicInteger> updateCounterCaffeineConfig() {
        return Caffeine
                .newBuilder()
                .initialCapacity(10_000)
                .expireAfterAccess(10, TimeUnit.MINUTES) //The all data of batch will be removed after 10 minutes.
                .build();
    }

}
