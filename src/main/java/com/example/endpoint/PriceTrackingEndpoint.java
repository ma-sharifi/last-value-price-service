package com.example.endpoint;

import com.example.model.Instrument;
import com.example.model.PriceData;
import com.example.service.PriceTrackingService;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Mahdi Sharifi
 */
@RestController
@RequestMapping("/instruments")
@Slf4j
public class PriceTrackingEndpoint implements PriceTrackerResource{

    private final PriceTrackingService priceTrackingService;

    public PriceTrackingEndpoint(PriceTrackingService priceTrackingService) {
        this.priceTrackingService = priceTrackingService;
    }

    @Override
    public String startBatchRun() {
        return priceTrackingService.startBatchRun();
    }

    @Override
    public void uploadPriceData(String batchId, List<PriceData> priceDataList) {
        priceTrackingService.uploadPriceData(batchId,priceDataList);
    }

    @Override
    public void completeBatchRun(String batchId) {
        priceTrackingService.completeBatchRun(batchId);
    }

    @Override
    public void cancelBatchRun(String batchId) {
        priceTrackingService.cancelBatchRun(batchId);
    }

    @Override
    public Instrument getLastPrice(String instrumentId) throws RuntimeException {
        return priceTrackingService.getLastPrice(instrumentId);
    }

    //---------------- This part developed because of test ----------
    @PostMapping("")
    @ResponseStatus(code = HttpStatus.CREATED)
    public  long createInstrument(@RequestBody List<Instrument> priceDataList) {
        priceTrackingService.putAll(priceDataList);
        return priceTrackingService.size();
    }
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Instrument> getAll(){
        return priceTrackingService.getAll().values().stream().toList();
    }

    @Override
    public int getUpdateCounter(String batchId) {
        return priceTrackingService.updateCounter(batchId);
    }

    @Override
    public String storageName() {
        return priceTrackingService.storageName();
    }

}
