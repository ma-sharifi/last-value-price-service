package com.example.model;

import java.time.LocalDateTime;

/**
 * @author Mahdi Sharifi
 * * Define immutable Instrument for sake of use  this object easily in mutltithread appilicaion
 */

public record Instrument(String id, LocalDateTime updatedAt, int price) {
    public static Instrument of(Instrument old) {return new Instrument(old.id, old.updatedAt, old.price);}
}
