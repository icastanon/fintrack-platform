package com.fintrack.apiservice.account.mapper;

import com.fintrack.apiservice.account.dto.FinancialAccountResponse;
import com.fintrack.apiservice.account.entity.FinancialAccount;
import org.springframework.stereotype.Component;

@Component
public class FinancialAccountMapper {

    public FinancialAccountResponse toResponse(FinancialAccount account) {
        FinancialAccountResponse response = new FinancialAccountResponse();

        response.setId(account.getId());
        response.setName(account.getName());
        response.setAccountType(account.getAccountType());
        response.setCurrency(account.getUser().getCurrency().name());
        response.setOpeningBalance(account.getOpeningBalance());
        response.setCurrentBalance(account.getCurrentBalance());
        response.setStatus(account.getStatus());
        response.setVersion(account.getVersion());
        response.setCreatedAt(account.getCreatedAt());
        response.setUpdatedAt(account.getUpdatedAt());

        return response;
    }
}