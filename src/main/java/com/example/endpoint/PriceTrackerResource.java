package com.example.endpoint;

import com.example.model.Instrument;
import com.example.model.PriceData;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author Mahdi Sharifi
 */
public interface PriceTrackerResource {

    @PostMapping(value = "/batch/start", produces = MediaType.TEXT_PLAIN_VALUE)//This is post, because doesn't get cached.
    @ResponseStatus(code = HttpStatus.OK)
    String startBatchRun();//200

    @PostMapping(value = "/batch/{batchId}/upload", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(code = HttpStatus.ACCEPTED)
    void uploadPriceData(@PathVariable("batchId") String batchId, @RequestBody List<PriceData> priceDataList) throws IllegalStateException;

    @PostMapping(value = "/batch/{batchId}/complete")//202
    @ResponseStatus(code = HttpStatus.CREATED)
    void completeBatchRun(@PathVariable("batchId") String batchId) throws IllegalStateException;

    @PostMapping(value = "/batch/{batchId}/cancel")
    @ResponseStatus(code = HttpStatus.OK)
    void cancelBatchRun(@PathVariable("batchId") String batchId) throws IllegalStateException;

    @GetMapping(value = "/{instrumentId}",produces = MediaType.APPLICATION_JSON_VALUE)//200
    Instrument getLastPrice(@PathVariable("instrumentId") String instrumentId)  throws RuntimeException;

    @GetMapping(value = "/counter")//200
    int getUpdateCounter(String batchId);

    @GetMapping(value = "/storage")//200
    String storageName();

}
