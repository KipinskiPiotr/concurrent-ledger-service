package com.concurrent_ledger_service.web;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class AccountControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void createsAccountWithGeneratedIdWhenNoneGiven() throws Exception {
        mvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId", notNullValue()));
    }

    @Test
    void createsAccountWithGivenIdAndInitialBalance() throws Exception {
        mvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"alice\",\"initialBalance\":10000}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/accounts/alice"));

        mvc.perform(get("/accounts/alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(10000));
    }

    @Test
    void gettingAnUnknownAccountReturns404() throws Exception {
        mvc.perform(get("/accounts/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void creatingTheSameAccountIdTwiceReturns409() throws Exception {
        mvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"dup\"}"))
                .andExpect(status().isCreated());

        mvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"dup\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ACCOUNT_ALREADY_EXISTS"));
    }
}
