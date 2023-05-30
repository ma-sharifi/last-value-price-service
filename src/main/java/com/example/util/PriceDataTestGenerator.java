package com.example.util;

/**
 * @author Mahdi Sharifi
 */

import com.example.model.Instrument;
import com.example.model.PriceData;
import com.google.gson.*;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomStringUtils;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface PriceDataTestGenerator {

    static Gson GSON = new GsonBuilder().setPrettyPrinting().registerTypeAdapter(LocalDateTime.class, new JsonSerializer<LocalDateTime>() {
        @Override
        public JsonElement serialize(LocalDateTime localDateTime, Type type, JsonSerializationContext jsonSerializationContext) {
            String formattedDateTime = localDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return new JsonPrimitive(formattedDateTime);
        }
    }).create();



    record Pair(List<PriceData> priceDataList, Map<String,Instrument> instrumentMap){}

    @SneakyThrows
     static Pair generateRandomPriceDataList(int size)  {
        List<PriceData> priceDataList = new ArrayList<>();
        List<Instrument> instrumentList = new ArrayList<>();
        Map<String,Instrument> instrumentMap = new HashMap<>();

        for (int i = 0; i < size; i++) {
            var randomId = RandomStringUtils.randomNumeric(5)+"-"+ RandomStringUtils.randomAlphabetic(5);
            var threadLocalRandom=ThreadLocalRandom.current();
            var randomPayload = threadLocalRandom.nextInt(1,10000);
            var randomAsOf = generateRandomDateTime(0);
            var instrument = new Instrument(randomId, randomAsOf, randomPayload);
            var asOfNew =  LocalDateTime.ofEpochSecond(randomAsOf.toEpochSecond(ZoneOffset.UTC)+threadLocalRandom.nextInt(10000,10000000),0, ZoneOffset.UTC);//generateRandomDateTime(1000000);
            var priceData = new PriceData(randomId, asOfNew, randomPayload);//Generate PriceData with time after that instrument
            instrumentList.add(instrument);
            priceDataList.add(priceData);
        }
         var json = GSON.toJson(instrumentList);
         Files.write(Paths.get("./instrument-random-test.txt"), json.getBytes());

         json = GSON.toJson(priceDataList);
         Files.write(Paths.get("./pricedata-random-test.txt"), json.getBytes());

         instrumentMap=instrumentList.stream().collect(Collectors.toMap(x->x.id(), Function.identity()));
         return new Pair(priceDataList,instrumentMap);
    }


    private static LocalDateTime generateRandomDateTime(long timeDifference) {
        // Generate a random LocalDateTime within a specified range
        var startDateTime = LocalDateTime.of(2020, 1, 1, 0, 0);
        var endDateTime = LocalDateTime.of(2023, 12, 31, 23, 59);

        long startTimestamp = startDateTime.toEpochSecond(ZoneOffset.UTC);
        long endTimestamp = endDateTime.toEpochSecond(ZoneOffset.UTC)+timeDifference;

        var random = ThreadLocalRandom.current();
        long randomTimestamp = startTimestamp + random.nextLong(endTimestamp - startTimestamp);

        return LocalDateTime.ofEpochSecond(randomTimestamp, 0, ZoneOffset.UTC);
    }

}
