package com.concurrent_ledger_service.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TransferControllerTest {

    @Autowired
    private MockMvc mvc;

    private void createAccount(String accountId, long initialBalance) throws Exception {
        mvc.perform(post("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"" + accountId + "\",\"initialBalance\":" + initialBalance + "}"));
    }

    @Test
    void transfersMoneyBetweenAccounts() throws Exception {
        createAccount("alice", 10_000);
        createAccount("bob", 0);

        mvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":\"alice\",\"toAccountId\":\"bob\",\"amount\":2500}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(2500));

        mvc.perform(get("/accounts/alice")).andExpect(jsonPath("$.balance").value(7500));
        mvc.perform(get("/accounts/bob")).andExpect(jsonPath("$.balance").value(2500));
    }

    @Test
    void insufficientFundsReturns422AndLeavesBalancesUnchanged() throws Exception {
        createAccount("alice2", 100);
        createAccount("bob2", 0);

        mvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":\"alice2\",\"toAccountId\":\"bob2\",\"amount\":101}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_FUNDS"));

        mvc.perform(get("/accounts/alice2")).andExpect(jsonPath("$.balance").value(100));
    }

    @Test
    void transferFromUnknownAccountReturns404() throws Exception {
        createAccount("bob3", 0);

        mvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":\"ghost\",\"toAccountId\":\"bob3\",\"amount\":100}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void zeroAmountTransferReturns400() throws Exception {
        createAccount("alice4", 100);
        createAccount("bob4", 0);

        mvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":\"alice4\",\"toAccountId\":\"bob4\",\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_AMOUNT"));
    }

    @Test
    void repeatingTheSameIdempotencyKeyDoesNotApplyTheTransferTwice() throws Exception {
        createAccount("alice5", 10_000);
        createAccount("bob5", 0);
        String body = "{\"fromAccountId\":\"alice5\",\"toAccountId\":\"bob5\",\"amount\":100}";

        mvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "http-key-1")
                        .content(body))
                .andExpect(status().isCreated());

        mvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "http-key-1")
                        .content(body))
                .andExpect(status().isCreated());

        mvc.perform(get("/accounts/bob5")).andExpect(jsonPath("$.balance").value(100));
    }

    @Test
    void reusingAnIdempotencyKeyWithADifferentBodyReturns409() throws Exception {
        createAccount("alice6", 10_000);
        createAccount("bob6", 0);
        createAccount("carol6", 0);

        mvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "http-key-2")
                        .content("{\"fromAccountId\":\"alice6\",\"toAccountId\":\"bob6\",\"amount\":100}"))
                .andExpect(status().isCreated());

        mvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "http-key-2")
                        .content("{\"fromAccountId\":\"alice6\",\"toAccountId\":\"carol6\",\"amount\":100}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("IDEMPOTENCY_KEY_REUSED"));
    }
}
