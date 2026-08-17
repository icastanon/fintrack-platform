package com.fintrack.apiservice.summary.projection;

import java.math.BigDecimal;

public interface BudgetUsageProjection {

    Long getBudgetId();

    Long getCategoryId();

    String getCategoryName();

    BigDecimal getBudgetAmount();

    Integer getWarningThresholdPercentage();

    BigDecimal getSpentAmount();
}