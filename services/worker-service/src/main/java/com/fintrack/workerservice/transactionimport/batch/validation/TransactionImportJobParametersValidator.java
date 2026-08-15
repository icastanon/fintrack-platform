package com.fintrack.workerservice.transactionimport.batch.validation;

import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameter;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersValidator;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class TransactionImportJobParametersValidator implements JobParametersValidator {

    private static final String IMPORT_ID = "importId";
    private static final String ACCOUNT_ID = "accountId";
    private static final String USER_ID = "userId";
    private static final String SOURCE_OBJECT_KEY = "sourceObjectKey";
    private static final String PROCESSING_OWNER = "processingOwner";
    private static final String PROCESSING_FENCING_TOKEN = "processingFencingToken";

    private static final Set<String> EXPECTED_PARAMETER_NAMES =
            Set.of(IMPORT_ID, ACCOUNT_ID, USER_ID, SOURCE_OBJECT_KEY, PROCESSING_OWNER, PROCESSING_FENCING_TOKEN);

    private static final int MAXIMUM_OBJECT_KEY_LENGTH = 1024;
    private static final int MAXIMUM_PROCESSING_OWNER_LENGTH = 100;

    @Override
    public void validate(JobParameters parameters) throws InvalidJobParametersException {
        if (parameters == null) {
            throw new InvalidJobParametersException("Transaction import job parameters are required");
        }

        rejectUnexpectedParameters(parameters);

        requirePositiveLong(parameters, IMPORT_ID, true);
        requirePositiveLong(parameters, ACCOUNT_ID, false);
        requirePositiveLong(parameters, USER_ID, false);
        requireSourceObjectKey(parameters);
        requireProcessingOwner(parameters);
        requirePositiveLong(parameters, PROCESSING_FENCING_TOKEN, false);
    }

    private void rejectUnexpectedParameters(JobParameters parameters) throws InvalidJobParametersException {
        for (JobParameter<?> parameter : parameters) {
            if (!EXPECTED_PARAMETER_NAMES.contains(parameter.name())) {
                throw new InvalidJobParametersException(
                        "Unexpected transaction import job parameter: " + parameter.name()
                );
            }
        }
    }

    private void requirePositiveLong(JobParameters parameters, String parameterName,
            boolean identifying) throws InvalidJobParametersException {
        JobParameter<?> parameter = parameters.getParameter(parameterName);

        if (parameter == null
                || !Long.class.equals(parameter.type())
                || !(parameter.value() instanceof Long value)
                || value <= 0) {
            throw new InvalidJobParametersException(
                    "Transaction import job parameter " + parameterName + " must be a positive Long");
        }

        validateIdentifyingFlag(parameter, parameterName, identifying);
    }

    private void requireSourceObjectKey(JobParameters parameters) throws InvalidJobParametersException {
        JobParameter<?> parameter = parameters.getParameter(SOURCE_OBJECT_KEY);

        if (parameter == null
                || !String.class.equals(parameter.type())
                || !(parameter.value() instanceof String objectKey)
                || objectKey.isBlank()
                || objectKey.length() > MAXIMUM_OBJECT_KEY_LENGTH) {
            throw new InvalidJobParametersException(
                    "Transaction import job parameter sourceObjectKey must be a nonblank String no longer than 1024 characters"
            );
        }

        validateIdentifyingFlag(parameter, SOURCE_OBJECT_KEY, false);
    }

    private void requireProcessingOwner(JobParameters parameters)
            throws InvalidJobParametersException {
        JobParameter<?> parameter = parameters.getParameter(PROCESSING_OWNER);

        if (parameter == null
                || !String.class.equals(parameter.type())
                || !(parameter.value() instanceof String processingOwner)
                || processingOwner.isBlank()
                || processingOwner.length() > MAXIMUM_PROCESSING_OWNER_LENGTH) {
            throw new InvalidJobParametersException(
                    "Transaction import job parameter processingOwner must be a nonblank String no longer than 100 characters"
            );
        }

        validateIdentifyingFlag(parameter, PROCESSING_OWNER, false);
    }

    private void validateIdentifyingFlag(JobParameter<?> parameter, String parameterName,
                                         boolean expected) throws InvalidJobParametersException {
        if (parameter.identifying() != expected) {
            throw new InvalidJobParametersException(
                    "Transaction import job parameter " + parameterName
                            + " has an invalid identifying flag"
            );
        }
    }
}