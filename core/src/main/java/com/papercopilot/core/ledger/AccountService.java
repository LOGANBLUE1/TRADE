package com.papercopilot.core.ledger;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.papercopilot.core.config.CoreProperties;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final CoreProperties props;

    AccountService(AccountRepository accounts, CoreProperties props) {
        this.accounts = accounts;
        this.props = props;
    }

    /** Creates the paper account on first use, then takes its row lock for the rest of the transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Account lock(String userId) {
        accounts.insertIfAbsent(UUID.randomUUID(), userId, props.trading().startingCash());
        return accounts.lockByUserId(userId).orElseThrow();
    }
}
