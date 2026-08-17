package com.fintrack.apiservice.summary.projection;

import java.math.BigDecimal;

public interface MonthlyCashFlowProjection {

    BigDecimal getIncome();

    BigDecimal getExpenses();
}