package com.papercopilot.ai.credits;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.papercopilot.ai.AiProperties;

/** Meters LLM inference we pay for. One credit per successful AI call. */
@Service
public class AiCreditService {

    private final JdbcTemplate jdbc;
    private final AiProperties props;

    public AiCreditService(JdbcTemplate jdbc, AiProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    public int balance(String userId) {
        jdbc.update("insert into ai_credits (user_id, balance) values (?, ?) on conflict (user_id) do nothing",
                userId, props.startingCredits());
        Integer balance = jdbc.queryForObject("select balance from ai_credits where user_id = ?", Integer.class,
                userId);
        return balance == null ? 0 : balance;
    }

    /** Atomic: concurrent calls can never take the balance below zero. */
    public void consume(String userId) {
        balance(userId);
        int updated = jdbc.update(
                "update ai_credits set balance = balance - 1, updated_at = now() where user_id = ? and balance >= 1",
                userId);
        if (updated == 0) {
            throw new AiCreditsExhaustedException();
        }
    }
}
