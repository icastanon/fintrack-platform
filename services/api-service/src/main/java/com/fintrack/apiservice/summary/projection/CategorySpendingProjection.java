package com.fintrack.apiservice.summary.projection;

import java.math.BigDecimal;

public interface CategorySpendingProjection {

    Long getCategoryId();

    String getCategoryName();

    BigDecimal getSpentAmount();
}