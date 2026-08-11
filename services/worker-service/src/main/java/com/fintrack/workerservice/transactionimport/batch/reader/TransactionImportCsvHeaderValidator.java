package com.fintrack.workerservice.transactionimport.batch.reader;

import com.fintrack.workerservice.transactionimport.exception.InvalidTransactionImportHeaderException;
import org.springframework.batch.infrastructure.item.file.LineCallbackHandler;
import org.springframework.stereotype.Component;

@Component
public class TransactionImportCsvHeaderValidator implements LineCallbackHandler {

    private static final String EXPECTED_HEADER =
            "transaction_date,transaction_type,amount,merchant,description";

    @Override
    public void handleLine(String line) {
        String header = removeUtf8ByteOrderMark(line);

        if (!EXPECTED_HEADER.equals(header)) {
            throw new InvalidTransactionImportHeaderException(EXPECTED_HEADER);
        }
    }

    private String removeUtf8ByteOrderMark(String line) {
        if (line != null && line.startsWith("\uFEFF")) {
            return line.substring(1);
        }

        return line;
    }
}