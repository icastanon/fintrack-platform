package com.fintrack.apiservice.summary.projection;

import java.math.BigDecimal;

public interface AccountSpendingProjection {

    Long getAccountId();

    String getAccountName();

    BigDecimal getSpentAmount();
}