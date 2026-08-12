package com.fintrack.workerservice.transactionimport.batch.validation;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionImportJobParametersValidatorTest {

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
                .hasMessage(
                        "Transaction import job parameter userId must be a positive Long");
    }

    @Test
    void validateRejectsUnexpectedParameter() {
        JobParameters parameters = new JobParametersBuilder(validParameters())
                .addString("run.id", "unexpected", true)
                .toJobParameters();

        assertThatThrownBy(() -> validator.validate(parameters))
                .hasMessage(
                        "Unexpected transaction import job parameter: run.id");
    }

    @Test
    void validateRejectsNonPositiveLongParameter() {
        JobParameters parameters = new JobParametersBuilder()
                .addLong("importId", 0L, true)
                .addLong("accountId", 22L, false)
                .addLong("userId", 9L, false)
                .addString(
                        "sourceObjectKey",
                        "imports/9/import-uuid/source.csv",
                        false)
                .toJobParameters();

        assertThatThrownBy(() -> validator.validate(parameters))
                .hasMessage(
                        "Transaction import job parameter importId must be a positive Long");
    }

    @Test
    void validateRejectsLongParameterWithWrongType() {
        JobParameters parameters = new JobParametersBuilder()
                .addString("importId", "41", true)
                .addLong("accountId", 22L, false)
                .addLong("userId", 9L, false)
                .addString(
                        "sourceObjectKey",
                        "imports/9/import-uuid/source.csv",
                        false)
                .toJobParameters();

        assertThatThrownBy(() -> validator.validate(parameters))
                .hasMessage(
                        "Transaction import job parameter importId must be a positive Long");
    }

    @Test
    void validateRejectsNonIdentifyingImportId() {
        JobParameters parameters = new JobParametersBuilder()
                .addLong("importId", 41L, false)
                .addLong("accountId", 22L, false)
                .addLong("userId", 9L, false)
                .addString(
                        "sourceObjectKey",
                        "imports/9/import-uuid/source.csv",
                        false)
                .toJobParameters();

        assertThatThrownBy(() -> validator.validate(parameters))
                .hasMessage(
                        "Transaction import job parameter importId has an invalid identifying flag");
    }

    @Test
    void validateRejectsIdentifyingAccountId() {
        JobParameters parameters = new JobParametersBuilder()
                .addLong("importId", 41L, true)
                .addLong("accountId", 22L, true)
                .addLong("userId", 9L, false)
                .addString(
                        "sourceObjectKey",
                        "imports/9/import-uuid/source.csv",
                        false)
                .toJobParameters();

        assertThatThrownBy(() -> validator.validate(parameters))
                .hasMessage(
                        "Transaction import job parameter accountId has an invalid identifying flag");
    }

    @Test
    void validateRejectsBlankSourceObjectKey() {
        JobParameters parameters = new JobParametersBuilder()
                .addLong("importId", 41L, true)
                .addLong("accountId", 22L, false)
                .addLong("userId", 9L, false)
                .addString("sourceObjectKey", "   ", false)
                .toJobParameters();

        assertThatThrownBy(() -> validator.validate(parameters))
                .hasMessage(
                        "Transaction import job parameter sourceObjectKey must be a nonblank String no longer than 1024 characters");
    }

    @Test
    void validateRejectsOversizedSourceObjectKey() {
        JobParameters parameters = new JobParametersBuilder()
                .addLong("importId", 41L, true)
                .addLong("accountId", 22L, false)
                .addLong("userId", 9L, false)
                .addString("sourceObjectKey", "a".repeat(1025), false)
                .toJobParameters();

        assertThatThrownBy(() -> validator.validate(parameters))
                .hasMessage(
                        "Transaction import job parameter sourceObjectKey must be a nonblank String no longer than 1024 characters");
    }

    private JobParameters validParameters() {
        return new JobParametersBuilder()
                .addLong("importId", 41L, true)
                .addLong("accountId", 22L, false)
                .addLong("userId", 9L, false)
                .addString(
                        "sourceObjectKey",
                        "imports/9/import-uuid/source.csv",
                        false)
                .toJobParameters();
    }

    private JobParameters withoutParameter(JobParameters parameters, String parameterName) {
        return new JobParameters(
                parameters.parameters().stream()
                        .filter(parameter -> !parameter.name().equals(parameterName))
                        .collect(Collectors.toSet()));
    }
}