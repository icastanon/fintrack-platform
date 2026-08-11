package com.fintrack.apiservice.transactionimport.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TransactionImportRejectedOutput {

    private final String fileName;
    private final byte[] content;
}