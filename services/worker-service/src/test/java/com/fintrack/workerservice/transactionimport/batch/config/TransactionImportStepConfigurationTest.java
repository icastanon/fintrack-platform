package com.fintrack.workerservice.transactionimport.batch.config;

import com.fintrack.workerservice.transaction.entity.TransactionType;
import com.fintrack.workerservice.transactionimport.batch.listener.TransactionImportSkipListener;
import com.fintrack.workerservice.transactionimport.batch.model.TransactionImportCsvRow;
import com.fintrack.workerservice.transactionimport.batch.model.ValidatedTransactionImportRow;
import com.fintrack.workerservice.transactionimport.batch.processor.TransactionImportItemProcessor;
import com.fintrack.workerservice.transactionimport.batch.writer.TransactionImportItemWriter;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportRowValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.repository.support.ResourcelessJobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportStepConfigurationTest {

    @Mock
    private FlatFileItemReader<TransactionImportCsvRow> transactionImportCsvReader;

    @Mock
    private TransactionImportItemProcessor transactionImportItemProcessor;

    @Mock
    private TransactionImportItemWriter transactionImportItemWriter;

    @Mock
    private TransactionImportSkipListener transactionImportSkipListener;

    private Step transactionImportStep;

    @BeforeEach
    void setUp() {
        TransactionImportStepConfiguration configuration =
                new TransactionImportStepConfiguration();

        transactionImportStep = configuration.transactionImportStep(
                new ResourcelessJobRepository(),
                new ResourcelessTransactionManager(),
                transactionImportCsvReader,
                transactionImportItemProcessor,
                transactionImportItemWriter,
                transactionImportSkipListener
        );
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void stepSkipsInvalidRowStagesRejectionAndWritesValidRows() throws Exception {
        TransactionImportCsvRow firstRow = csvRow(2);
        TransactionImportCsvRow invalidRow = csvRow(3);
        TransactionImportCsvRow thirdRow = csvRow(4);

        ValidatedTransactionImportRow firstValidatedRow = validatedRow(2);
        ValidatedTransactionImportRow thirdValidatedRow = validatedRow(4);

        TransactionImportRowValidationException validationException =
                new TransactionImportRowValidationException(
                        3,
                        "amount must be valid"
                );

        when(transactionImportCsvReader.read())
                .thenReturn(firstRow, invalidRow, thirdRow, null);
        when(transactionImportItemProcessor.process(firstRow))
                .thenReturn(firstValidatedRow);
        when(transactionImportItemProcessor.process(invalidRow))
                .thenThrow(validationException);
        when(transactionImportItemProcessor.process(thirdRow))
                .thenReturn(thirdValidatedRow);

        StepExecution stepExecution = stepExecution();

        transactionImportStep.execute(stepExecution);

        assertThat(stepExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(stepExecution.getReadCount()).isEqualTo(3);
        assertThat(stepExecution.getWriteCount()).isEqualTo(2);
        assertThat(stepExecution.getProcessSkipCount()).isEqualTo(1);
        assertThat(stepExecution.getSkipCount()).isEqualTo(1);

        ArgumentCaptor<Chunk> chunkCaptor = ArgumentCaptor.forClass(Chunk.class);

        verify(transactionImportItemWriter).write(chunkCaptor.capture());
        verify(transactionImportSkipListener)
                .onSkipInProcess(invalidRow, validationException);

        assertThat(chunkCaptor.getValue().getItems())
                .containsExactly(firstValidatedRow, thirdValidatedRow);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void stepSkipsMalformedCsvRecordAndWritesValidRow() throws Exception {
        String malformedRecord = "2026-08-10,EXPENSE,12.50,STARBUCKS";

        FlatFileParseException parseException = new FlatFileParseException(
                "Failed to parse CSV record",
                malformedRecord,
                2
        );

        TransactionImportCsvRow validRow = csvRow(3);
        ValidatedTransactionImportRow validatedRow = validatedRow(3);

        when(transactionImportCsvReader.read())
                .thenThrow(parseException)
                .thenReturn(validRow, null);
        when(transactionImportItemProcessor.process(validRow))
                .thenReturn(validatedRow);

        StepExecution stepExecution = stepExecution();

        transactionImportStep.execute(stepExecution);

        assertThat(stepExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(stepExecution.getReadCount()).isEqualTo(1);
        assertThat(stepExecution.getReadSkipCount()).isEqualTo(1);
        assertThat(stepExecution.getWriteCount()).isEqualTo(1);
        assertThat(stepExecution.getSkipCount()).isEqualTo(1);

        ArgumentCaptor<Chunk> chunkCaptor = ArgumentCaptor.forClass(Chunk.class);

        verify(transactionImportItemWriter).write(chunkCaptor.capture());
        verify(transactionImportSkipListener).onSkipInRead(parseException);

        assertThat(chunkCaptor.getValue().getItems())
                .containsExactly(validatedRow);
    }

    @Test
    void stepFailsWhenValidationSkipLimitIsExceeded() throws Exception {
        AtomicInteger rowNumber = new AtomicInteger(2);

        when(transactionImportCsvReader.read()).thenAnswer(invocation -> {
            int currentRowNumber = rowNumber.getAndIncrement();

            if (currentRowNumber > 102) {
                return null;
            }

            return csvRow(currentRowNumber);
        });

        when(transactionImportItemProcessor.process(
                org.mockito.ArgumentMatchers.any(TransactionImportCsvRow.class)))
                .thenAnswer(invocation -> {
                    TransactionImportCsvRow row = invocation.getArgument(0);

                    throw new TransactionImportRowValidationException(
                            row.getRowNumber(),
                            "invalid row"
                    );
                });

        StepExecution stepExecution = stepExecution();

        transactionImportStep.execute(stepExecution);

        assertThat(stepExecution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(stepExecution.getProcessSkipCount()).isEqualTo(100);
        assertThat(stepExecution.getWriteCount()).isZero();

        verify(transactionImportItemWriter, never())
                .write(argThat(chunk -> !chunk.isEmpty()));
    }

    @Test
    void stepDoesNotSkipUnexpectedProcessingFailure() throws Exception {
        TransactionImportCsvRow row = csvRow(2);

        when(transactionImportCsvReader.read()).thenReturn(row, null);
        when(transactionImportItemProcessor.process(row))
                .thenThrow(new IllegalStateException("Unexpected processing failure"));

        StepExecution stepExecution = stepExecution();

        transactionImportStep.execute(stepExecution);

        assertThat(stepExecution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(stepExecution.getSkipCount()).isZero();
        assertThat(stepExecution.getWriteCount()).isZero();

        verify(transactionImportItemWriter, never())
                .write(argThat(chunk -> !chunk.isEmpty()));
        verify(transactionImportSkipListener, never())
                .onSkipInProcess(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()
                );
    }

    private StepExecution stepExecution() {
        JobInstance jobInstance = new JobInstance(1L, "transactionImportJob");
        JobExecution jobExecution =
                new JobExecution(1L, jobInstance, new JobParameters());

        return new StepExecution(
                1L,
                "transactionImportStep",
                jobExecution
        );
    }

    private TransactionImportCsvRow csvRow(int rowNumber) {
        String rawRecord =
                "2026-08-10,EXPENSE,12.50,STARBUCKS,Coffee";

        return new TransactionImportCsvRow(
                rowNumber,
                "2026-08-10",
                "EXPENSE",
                "12.50",
                "STARBUCKS",
                "Coffee",
                rawRecord
        );
    }

    private ValidatedTransactionImportRow validatedRow(int rowNumber) {
        return new ValidatedTransactionImportRow(
                rowNumber,
                LocalDate.of(2026, 8, 10),
                TransactionType.EXPENSE,
                new BigDecimal("12.50"),
                "STARBUCKS",
                "Coffee",
                4L
        );
    }
}