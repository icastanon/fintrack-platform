package com.fintrack.workerservice.transactionimport.batch.reader;

import com.fintrack.workerservice.transactionimport.batch.model.TransactionImportCsvRow;
import org.springframework.batch.infrastructure.item.file.LineMapper;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.infrastructure.item.file.transform.FieldSet;
import org.springframework.stereotype.Component;

@Component
public class TransactionImportCsvLineMapper implements LineMapper<TransactionImportCsvRow> {

    private final DelimitedLineTokenizer lineTokenizer;

    public TransactionImportCsvLineMapper() {
        lineTokenizer = new DelimitedLineTokenizer(DelimitedLineTokenizer.DELIMITER_COMMA);
        lineTokenizer.setNames("transaction_date", "transaction_type", "amount", "merchant", "description");
        lineTokenizer.setStrict(true);
    }

    @Override
    public TransactionImportCsvRow mapLine(String line, int lineNumber) {
        FieldSet fieldSet = lineTokenizer.tokenize(line);

        return new TransactionImportCsvRow(lineNumber,
                fieldSet.readRawString("transaction_date"),
                fieldSet.readRawString("transaction_type"),
                fieldSet.readRawString("amount"),
                fieldSet.readRawString("merchant"),
                fieldSet.readRawString("description"));
    }
}