package com.example.storage;

import com.example.model.Instrument;
import com.example.storage.annotation.Storage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Mahdi Sharifi
 * We can use ConcurrentHashMap as storage
 */
@Service
@Storage(Storage.Type.MAP)
@Slf4j
public class MapStorageService implements StorageService<String, Instrument> {

    private static final Map<String, Instrument> storage = new ConcurrentHashMap<>(100_000);
    //Java already supports WeakHashMap to use Weak References for the keys. But, there is no out-of-the-box solution to use the same for the values.

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
