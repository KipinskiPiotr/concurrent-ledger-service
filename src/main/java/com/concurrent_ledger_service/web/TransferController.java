package com.concurrent_ledger_service.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.concurrent_ledger_service.ledger.LedgerService;
import com.concurrent_ledger_service.ledger.Money;
import com.concurrent_ledger_service.ledger.TransferRequest;
import com.concurrent_ledger_service.ledger.TransferResult;
import com.concurrent_ledger_service.web.dto.TransferHttpRequest;
import com.concurrent_ledger_service.web.dto.TransferResponse;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final LedgerService ledgerService;

    public TransferController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> transfer(
            @RequestBody TransferHttpRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        TransferRequest coreRequest = new TransferRequest(
                request.fromAccountId(), request.toAccountId(), Money.ofCents(request.amount()));

        TransferResult result = ledgerService.transfer(coreRequest, idempotencyKey);

        TransferResponse response = new TransferResponse(
                result.transferId(),
                result.fromAccountId(),
                result.toAccountId(),
                result.amount().cents(),
                result.status());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
