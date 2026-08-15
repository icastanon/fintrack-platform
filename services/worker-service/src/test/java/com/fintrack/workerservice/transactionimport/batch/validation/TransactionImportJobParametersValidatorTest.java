package com.fintrack.workerservice.transactionimport.batch.validation;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionImportJobParametersValidatorTest {

    private static final String SOURCE_OBJECT_KEY = "imports/9/import-uuid/source.csv";
    private static final String PROCESSING_OWNER = "worker-a";

    private final TransactionImportJobParametersValidator validator =
            new TransactionImportJobParametersValidator();

    @Test
    void validateAcceptsValidParameters() {
        assertThatCode(() -> validator.validate(validParameters()))
                .doesNotThrowAnyException();
    }

    @Test
    void validateRejectsNullParameters() {
        assertThatThrownBy(() -> validator.validate(null))
                .hasMessage("Transaction import job parameters are required");
    }

    @Test
    void validateRejectsMissingRequiredParameter() {
        JobParameters parameters = withoutParameter(validParameters(), "userId");

        assertThatThrownBy(() -> validator.validate(parameters))
                .hasMessage("Transaction import job parameter userId must be a positive Long");
    }

    @Test
    void validateRejectsUnexpectedParameter() {
        JobParameters parameters = new JobParametersBuilder(validParameters())
                .addString("run.id", "unexpected", true)
                .toJobParameters();

        assertThatThrownBy(() -> validator.validate(parameters))
                .hasMessage("Unexpected transaction import job parameter: run.id");
    }

    @Test
    void validateRejectsNonPositiveLongParameter() {
        JobParameters parameters = new JobParametersBuilder(validParameters())
                .addLong("importId", 0L, true)
                .toJobParameters();

        assertThatThrownBy(() -> validator.validate(parameters))
                .hasMessage("Transaction import job parameter importId must be a positive Long");
    }

    @Test
    void validateRejectsLongParameterWithWrongType() {
        JobParameters parameters = new JobParametersBuilder(
                withoutParameter(validParameters(), "importId")
        )
                .addString("importId", "41", true)
                .toJobParameters();

        assertThatThrownBy(() -> validator.validate(parameters))
                .hasMessage("Transaction import job parameter importId must be a positive Long");
    }

    @Test
    void validateRejectsNonIdentifyingImportId() {
        JobParameters parameters = new JobParametersBuilder(validParameters())
                .addLong("importId", 41L, false)
                .toJobParameters();

        assertThatThrownBy(() -> validator.validate(parameters))
                .hasMessage(
                        "Transaction import job parameter importId has an invalid identifying flag"
                );
    }

    @Test
    void validateRejectsIdentifyingAccountId() {
        JobParameters parameters = new JobParametersBuilder(validParameters())
                .addLong("accountId", 22L, true)
                .toJobParameters();

        assertThatThrownBy(() -> validator.validate(parameters))
                .hasMessage(
                        "Transaction import job parameter accountId has an invalid identifying flag"
                );
    }

    @Test
    void validateRejectsBlankSourceObjectKey() {
        JobParameters parameters = new JobParametersBuilder(validParameters())
                .addString("sourceObjectKey", "   ", false)
                .toJobParameters();

        assertThatThrownBy(() -> validator.validate(parameters))
                .hasMessage(
                        "Transaction import job parameter sourceObjectKey must be a nonblank String no longer than 1024 characters"
                );
    }

    @Test
    void validateRejectsOversizedSourceObjectKey() {
        JobParameters parameters = new JobParametersBuilder(validParameters())
                .addString("sourceObjectKey", "a".repeat(1025), false)
                .toJobParameters();

        assertThatThrownBy(() -> validator.validate(parameters))
                .hasMessage(
                        "Transaction import job parameter sourceObjectKey must be a nonblank String no longer than 1024 characters"
                );
    }

    @Test
    void validateRejectsMissingProcessingOwner() {
        JobParameters parameters = withoutParameter(validParameters(), "processingOwner");

        assertThatThrownBy(() -> validator.validate(parameters))
                .hasMessage(
                        "Transaction import job parameter processingOwner must be a nonblank String no longer than 100 characters"
                );
    }

    @Test
    void validateRejectsBlankProcessingOwner() {
        JobParameters parameters = new JobParametersBuilder(validParameters())
                .addString("processingOwner", "   ", false)
                .toJobParameters();

        assertThatThrownBy(() -> validator.validate(parameters))
                .hasMessage(
                        "Transaction import job parameter processingOwner must be a nonblank String no longer than 100 characters"
                );
    }

    @Test
    void validateRejectsOversizedProcessingOwner() {
        JobParameters parameters = new JobParametersBuilder(validParameters())
                .addString("processingOwner", "a".repeat(101), false)
                .toJobParameters();

        assertThatThrownBy(() -> validator.validate(parameters))
                .hasMessage(
                        "Transaction import job parameter processingOwner must be a nonblank String no longer than 100 characters"
                );
    }

    @Test
    void validateRejectsIdentifyingProcessingOwner() {
        JobParameters parameters = new JobParametersBuilder(validParameters())
                .addString("processingOwner", PROCESSING_OWNER, true)
                .toJobParameters();

        assertThatThrownBy(() -> validator.validate(parameters))
                .hasMessage(
                        "Transaction import job parameter processingOwner has an invalid identifying flag"
                );
    }

    @Test
    void validateRejectsMissingProcessingFencingToken() {
        JobParameters parameters =
                withoutParameter(validParameters(), "processingFencingToken");

        assertThatThrownBy(() -> validator.validate(parameters))
                .hasMessage(
                        "Transaction import job parameter processingFencingToken must be a positive Long"
                );
    }

    @Test
    void validateRejectsNonPositiveProcessingFencingToken() {
        JobParameters parameters = new JobParametersBuilder(validParameters())
                .addLong("processingFencingToken", 0L, false)
                .toJobParameters();

        assertThatThrownBy(() -> validator.validate(parameters))
                .hasMessage(
                        "Transaction import job parameter processingFencingToken must be a positive Long"
                );
    }

    @Test
    void validateRejectsProcessingFencingTokenWithWrongType() {
        JobParameters parameters = new JobParametersBuilder(
                withoutParameter(validParameters(), "processingFencingToken")
        )
                .addString("processingFencingToken", "3", false)
                .toJobParameters();

        assertThatThrownBy(() -> validator.validate(parameters))
                .hasMessage(
                        "Transaction import job parameter processingFencingToken must be a positive Long"
                );
    }

    @Test
    void validateRejectsIdentifyingProcessingFencingToken() {
        JobParameters parameters = new JobParametersBuilder(validParameters())
                .addLong("processingFencingToken", 3L, true)
                .toJobParameters();

        assertThatThrownBy(() -> validator.validate(parameters))
                .hasMessage(
                        "Transaction import job parameter processingFencingToken has an invalid identifying flag"
                );
    }

    private JobParameters validParameters() {
        return new JobParametersBuilder()
                .addLong("importId", 41L, true)
                .addLong("accountId", 22L, false)
                .addLong("userId", 9L, false)
                .addString("sourceObjectKey", SOURCE_OBJECT_KEY, false)
                .addString("processingOwner", PROCESSING_OWNER, false)
                .addLong("processingFencingToken", 3L, false)
                .toJobParameters();
    }

    private JobParameters withoutParameter(JobParameters parameters, String parameterName) {
        return new JobParameters(
                parameters.parameters()
                        .stream()
                        .filter(parameter -> !parameter.name().equals(parameterName))
                        .collect(Collectors.toSet())
        );
    }
}