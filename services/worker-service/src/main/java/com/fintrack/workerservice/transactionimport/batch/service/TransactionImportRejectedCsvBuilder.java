package com.fintrack.workerservice.transactionimport.batch.service;

import com.fintrack.workerservice.transactionimport.entity.TransactionImportRejectedRowStaging;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

@Component
public class TransactionImportRejectedCsvBuilder {

    private static final String HEADER = "row_number,raw_record,failure_reason\n";

    public byte[] build(List<TransactionImportRejectedRowStaging> rejectedRows) {
        Objects.requireNonNull(rejectedRows, "Rejected rows are required");

        StringBuilder csv = new StringBuilder(HEADER);

        for (TransactionImportRejectedRowStaging rejectedRow : rejectedRows) {
            Objects.requireNonNull(rejectedRow, "Rejected row is required");

            csv.append(rejectedRow.getRowNumber()).append(',');
            csv.append(escapeCsvField(rejectedRow.getRawRecord())).append(',');
            csv.append(escapeCsvField(rejectedRow.getFailureReason())).append('\n');
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escapeCsvField(String value) {
        Objects.requireNonNull(value, "CSV field value is required");
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}