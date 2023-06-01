package com.example.storage;

import java.util.Map;

/**
 * @author Mahdi Sharifi
 * A facade on the storage layer
 */
public interface StorageService<K, V> {
    V get(K key);
    void put(K key,V value);
    long size();
    String name();

    void print();

    Map<K,V> getAll();

    void putAll(Map<K,V> instrumentMap);
}
