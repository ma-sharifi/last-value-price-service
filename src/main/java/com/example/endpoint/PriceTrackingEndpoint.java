package com.example.endpoint;

import com.example.model.Instrument;
import com.example.model.PriceData;
import com.example.service.PriceTrackingService;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
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
    @PostMapping("load")
    public long load(@RequestBody List<PriceData> priceDataList,@RequestParam(value = "psize" ,defaultValue = "1000") int partitionSize ) {
        String batchId= priceTrackingService.startBatchRun();
        List<List<PriceData>> partitionedData = Lists.partition(priceDataList, partitionSize);
        for ( List<PriceData> partitionedDatum : partitionedData) {
            priceTrackingService.uploadPriceData(batchId,partitionedDatum);
        }
        priceTrackingService.completeBatchRun(batchId);
        return priceTrackingService.updateCounter(batchId);
    }
    @PostMapping("")
    public  long saveToStorage(@RequestBody List<Instrument> priceDataList) {
        Map<String,Instrument> instrumentMap = priceDataList.stream().collect(Collectors.toMap(Instrument::id, Function.identity()));
        priceTrackingService.putAll(instrumentMap);
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
