package com.example.util;

/**
 * @author Mahdi Sharifi
 */

import com.example.model.Instrument;
import com.example.model.PriceData;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.index.qual.SameLen;

import java.io.IOException;
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

    Gson GSON = new GsonBuilder().setPrettyPrinting()
            .registerTypeAdapter(LocalDateTime.class, (JsonDeserializer<LocalDateTime>) (json, type, jsonDeserializationContext)
                    -> LocalDateTime.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .registerTypeAdapter(LocalDateTime.class, (JsonSerializer<LocalDateTime>) (localDateTime, type, jsonSerializationContext)
                    -> new JsonPrimitive(localDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))).create();

    record Pair(List<PriceData> priceDataList, List<Instrument> instrumentActualList) {}
    record Triple(List<PriceData> priceDataList, List<Instrument> instrumentActualList,List<Instrument> instrumentExpectedList) {}

    @SneakyThrows
    static Pair generateRandomPriceDataList(int size, int step) {
        List<PriceData> priceDataList = new ArrayList<>();
        List<Instrument> instrumentActualList = new ArrayList<>();
        List<Instrument> instrumentExpectedlList = new ArrayList<>();
        Map<String, Instrument> instrumentMap = new HashMap<>();
        for (int i = 0; i < size; i++) {
            var randomId = String.valueOf(i);
            var price = step;
            var payload = step * 10;
            var updatedAt = generateDateTime(step);
            var instrument = new Instrument(randomId, updatedAt, price);
            var asOfNew = LocalDateTime.ofEpochSecond(updatedAt.toEpochSecond(ZoneOffset.UTC) + 10, 0, ZoneOffset.UTC);// we can use random: generateRandomDateTime(1000000);
            var priceData = new PriceData(randomId, asOfNew, payload);//Generate PriceData with time after that instrument
            instrumentActualList.add(instrument);
            priceDataList.add(priceData);
            instrumentExpectedlList.add(new Instrument(instrument.id(), asOfNew, payload));
        }
        var json = GSON.toJson(instrumentActualList);
        Files.write(Paths.get("./instrument-actual.txt"), json.getBytes());

        json = GSON.toJson(priceDataList);
        Files.write(Paths.get("./pricedata.txt"), json.getBytes());

        json = GSON.toJson(instrumentExpectedlList);
        Files.write(Paths.get("./instrument-expected.txt"), json.getBytes());
        return new Pair(priceDataList, instrumentExpectedlList);
    }

    @SneakyThrows
    static Pair generatePriceInstrumentList(int size, int step) {
        List<PriceData> priceDataList = new ArrayList<>();
        List<Instrument> instrumentActualList = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            var randomId = String.valueOf(i);
            var price = step;
            var payload = step * 10;
            var updatedAt = generateDateTime(step);
            var instrument= new Instrument(randomId, updatedAt, price);
            instrumentActualList.add(instrument);

            var asOfNew = LocalDateTime.ofEpochSecond(updatedAt.toEpochSecond(ZoneOffset.UTC) + 10, 0, ZoneOffset.UTC);// we can use random: generateRandomDateTime(1000000);
            var priceData = new PriceData(randomId, asOfNew, payload);//Generate PriceData with time after that instrument
            priceDataList.add(priceData);
        }
        return new Pair(priceDataList, instrumentActualList);
    }

    @SneakyThrows
    static Triple generatePriceInstrumentListTriple(int size, int step) {
        List<PriceData> priceDataList = new ArrayList<>();
        List<Instrument> instrumentActualList = new ArrayList<>();
        List<Instrument> instrumentExpectedList = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            var id = String.valueOf(i);
            var price = step;
            var payload = step * 10;
            var updatedAt = generateDateTime(step);
            var instrument= new Instrument(id, updatedAt, price);
            instrumentActualList.add(instrument);

            var asOfNew = LocalDateTime.ofEpochSecond(updatedAt.toEpochSecond(ZoneOffset.UTC) + 10, 0, ZoneOffset.UTC);// we can use random: generateRandomDateTime(1000000);
            var priceData = new PriceData(id, asOfNew, payload);//Generate PriceData with time after that instrument
            priceDataList.add(priceData);
            instrumentExpectedList.add(new Instrument(id, asOfNew, payload));
        }
        return new Triple(priceDataList, instrumentActualList,instrumentExpectedList);
    }

    static List<Instrument> readInstrumentJsonFile(String filePath) throws IOException {
        var jsonArray = Files.readString(Paths.get(filePath));
        Type listType = new TypeToken<ArrayList<Instrument>>() {
        }.getType();
        return GSON.fromJson(jsonArray, listType);
    }
    static List<PriceData> readPriceDataJsonFile(String filePath) throws IOException {
        var jsonArray = Files.readString(Paths.get(filePath));
        Type listType = new TypeToken<ArrayList<PriceData>>() {
        }.getType();
        return GSON.fromJson(jsonArray, listType);
    }

    private static LocalDateTime generateRandomDateTime(long timeDifference) {
        // Generate a random LocalDateTime within a specified range
        var startDateTime = LocalDateTime.of(2010, 1, 1, 0, 0);
        var endDateTime = LocalDateTime.of(2024, 12, 31, 23, 59);

        long startTimestamp = startDateTime.toEpochSecond(ZoneOffset.UTC);
        long endTimestamp = endDateTime.toEpochSecond(ZoneOffset.UTC) + timeDifference;

        var random = ThreadLocalRandom.current();
        long randomTimestamp = startTimestamp + random.nextLong(endTimestamp - startTimestamp);

        return LocalDateTime.ofEpochSecond(randomTimestamp, 0, ZoneOffset.UTC);
    }
    private static LocalDateTime generateDateTime(long step) {
        var startDateTime = LocalDateTime.of(2010, 1, 1, 0, 0);
        long startTimestamp = startDateTime.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = startTimestamp + step;
        return LocalDateTime.ofEpochSecond(timeStamp, 0, ZoneOffset.UTC);
    }

}
