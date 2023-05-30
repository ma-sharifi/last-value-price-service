package com.example.model;

import java.time.LocalDateTime;

/**
 * @author Mahdi Sharifi
 */
public record PriceData(String id,LocalDateTime asOf,int payload) { }
