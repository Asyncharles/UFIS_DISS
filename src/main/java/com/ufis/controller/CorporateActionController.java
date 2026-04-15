package com.ufis.controller;

import com.ufis.dto.request.NameChangeRequest;
import com.ufis.dto.request.RedemptionRequest;
import com.ufis.dto.request.StockSplitRequest;
import com.ufis.dto.request.AcquisitionRequest;
import com.ufis.dto.request.MergerRequest;
import com.ufis.dto.request.SpinOffRequest;
import com.ufis.dto.response.CorporateActionResponse;
import com.ufis.dto.response.CorporateActionDetailResponse;
import com.ufis.dto.response.CorporateActionRecordResponse;
import com.ufis.service.CorporateActionDetailService;
import com.ufis.service.CorporateActionService;
import com.ufis.service.handler.AcquisitionHandler;
import com.ufis.service.handler.MergerHandler;
import com.ufis.service.handler.NameChangeHandler;
import com.ufis.service.handler.RedemptionHandler;
import com.ufis.service.handler.SpinOffHandler;
import com.ufis.service.handler.StockSplitHandler;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/corporate-action")
public class CorporateActionController {
    private final CorporateActionService corporateActionService;
    private final CorporateActionDetailService corporateActionDetailService;
    private final NameChangeHandler nameChangeHandler;
    private final RedemptionHandler redemptionHandler;
    private final StockSplitHandler stockSplitHandler;
    private final MergerHandler mergerHandler;
    private final AcquisitionHandler acquisitionHandler;
    private final SpinOffHandler spinOffHandler;

    public CorporateActionController(CorporateActionService corporateActionService, CorporateActionDetailService corporateActionDetailService, NameChangeHandler nameChangeHandler,
                                     RedemptionHandler redemptionHandler, StockSplitHandler stockSplitHandler, MergerHandler mergerHandler, AcquisitionHandler acquisitionHandler, SpinOffHandler spinOffHandler) {
        this.corporateActionService = corporateActionService;
        this.corporateActionDetailService = corporateActionDetailService;
        this.nameChangeHandler = nameChangeHandler;
        this.redemptionHandler = redemptionHandler;
        this.stockSplitHandler = stockSplitHandler;
        this.mergerHandler = mergerHandler;
        this.acquisitionHandler = acquisitionHandler;
        this.spinOffHandler = spinOffHandler;
    }

    @GetMapping("/recent")
    public List<CorporateActionRecordResponse> getRecent(@RequestParam(defaultValue = "20") int limit) {
        return corporateActionService.getRecent(limit);
    }

    @GetMapping("/{id}")
    public CorporateActionRecordResponse getById(@PathVariable UUID id) {
        return corporateActionService.getById(id);
    }

    @GetMapping("/{id}/detail")
    public CorporateActionDetailResponse getDetail(@PathVariable UUID id) {
        return corporateActionDetailService.getDetail(id);
    }

    @PostMapping("/name-change")
    @ResponseStatus(HttpStatus.CREATED)
    public CorporateActionResponse createNameChange(@Valid @RequestBody NameChangeRequest request) {
        return nameChangeHandler.handle(request);
    }

    @PostMapping("/redemption")
    @ResponseStatus(HttpStatus.CREATED)
    public CorporateActionResponse createRedemption(@Valid @RequestBody RedemptionRequest request) {
        return redemptionHandler.handle(request);
    }

    @PostMapping("/stock-split")
    @ResponseStatus(HttpStatus.CREATED)
    public CorporateActionResponse createStockSplit(@Valid @RequestBody StockSplitRequest request) {
        return stockSplitHandler.handle(request);
    }

    @PostMapping("/merger")
    @ResponseStatus(HttpStatus.CREATED)
    public CorporateActionResponse createMerger(@Valid @RequestBody MergerRequest request) {
        return mergerHandler.handle(request);
    }

    @PostMapping("/acquisition")
    @ResponseStatus(HttpStatus.CREATED)
    public CorporateActionResponse createAcquisition(@Valid @RequestBody AcquisitionRequest request) {
        return acquisitionHandler.handle(request);
    }

    @PostMapping("/spin-off")
    @ResponseStatus(HttpStatus.CREATED)
    public CorporateActionResponse createSpinOff(@Valid @RequestBody SpinOffRequest request) {
        return spinOffHandler.handle(request);
    }
}