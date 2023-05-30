package com.example.storage;

import com.example.model.Instrument;
import com.example.storage.annotation.Storage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Mahdi Sharifi
 */
@Service
@Storage(Storage.Type.MAP)
@Slf4j
public class MapStorageService implements StorageService<String, Instrument> {

    private static final Map<String, Instrument> storage = new ConcurrentHashMap<>(5000_000);
    //Java already supports WeakHashMap to use Weak References for the keys. But, there is no out-of-the-box solution to use the same for the values.
//    private static  Map<String, Instrument> storage= new WeakHashMap<>(10000);

    @Override
    public void put(String key, Instrument value) {
        storage.put(key, value);
    }

    @Override
    public Instrument get(String key) {
        if (!storage.containsKey(key)) return null;
        return storage.get(key);
    }

    @Override
    public long size() {
        return storage.size();
    }

    @Override
    public String name() {
        return "map";
    }

    @Override
    public void print() {
        storage.forEach((key, value) -> log.info("key: " + key + " ;value: " + value));
    }

    @Override
    public Map<String, Instrument> getAll() {
        return storage;
    }

    @Override
    public void putAll(Map<String, Instrument> instrumentMap) {
        storage.putAll(instrumentMap);
    }
}
