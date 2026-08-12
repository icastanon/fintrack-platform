package com.fintrack.workerservice.transactionimport.batch.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class AffectedBudgetKey {

    private final Long categoryId;
    private final LocalDate budgetMonth;
}