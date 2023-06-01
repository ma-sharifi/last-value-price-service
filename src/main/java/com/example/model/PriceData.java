package com.example.model;

import java.time.LocalDateTime;

/**
 * @author Mahdi Sharifi
 * payload can be an Object or a Generic, for the sake of simplicity I take it int
 */
public record PriceData(String id, LocalDateTime asOf, int payload) {
    public static PriceData of(PriceData priceData) {
        return new PriceData(priceData.id, priceData.asOf, priceData.payload);
    }
}
