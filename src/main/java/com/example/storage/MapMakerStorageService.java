package com.example.storage;

import com.example.model.Instrument;
import com.example.model.PriceData;
import com.example.storage.annotation.Storage;
import com.google.common.collect.MapMaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Mahdi Sharifi
 */
@Service
@Storage(Storage.Type.MAP_MAKER)
@Slf4j
public class MapMakerStorageService implements StorageService<String, Instrument> {

    // MapMaker provides simple builder methods to use WeakReference for both the keys and the values.
    private static Map<String, Instrument> storage = new MapMaker().makeMap();

    @Override
    public void put(String key, Instrument value){
        storage.put(key,value);
    }
    @Override
    public Instrument get(String key) {
        if(!storage.containsKey(key)) return null;
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
       storage.forEach((key,value)-> log.info("key: "+key+" ;value: "+value));
    }

    @Override
    public Map<String, Instrument> getAll() {
        return  storage;
    }

    @Override
    public void putAll(Map<String, Instrument> instrumentMap) {
        storage.putAll(instrumentMap);
    }

}
