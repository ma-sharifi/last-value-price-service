package com.example.storage;

import com.example.model.Instrument;
import com.example.storage.annotation.Storage;
import com.google.common.collect.MapMaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * @author Mahdi Sharifi
 * We can use Guava MapMaker as inmemeory strage
 */
@Service
@Storage(Storage.Type.MAP_MAKER)
@Slf4j
public class MapMakerStorageService implements StorageService<String, Instrument> {

    private static final Map<String, Instrument> storage = new MapMaker().makeMap();

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
        return "mapMaker";
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
