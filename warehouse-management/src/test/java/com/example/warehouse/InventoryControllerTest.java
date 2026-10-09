package com.example.warehouse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class InventoryControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void fullFlow() throws Exception {
        mvc.perform(post("/inventories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":2001,\"productName\":\"Mouse\",\"quantity\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(0));

        mvc.perform(post("/inventories/2001/stock-in").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(15));

        mvc.perform(put("/inventories/2001").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"Mouse Pro\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.quantity").value(15));

        // version cu -> 409
        mvc.perform(put("/inventories/2001").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"Mouse X\",\"version\":0}"))
                .andExpect(status().isConflict());

        mvc.perform(get("/inventories/2001/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].revisionType").value("ADD"));
    }

    @Test
    void notFoundAndValidation() throws Exception {
        mvc.perform(post("/inventories/999999/stock-in").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1}"))
                .andExpect(status().isNotFound());

        mvc.perform(post("/inventories/1/stock-in").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/inventories/999999/history"))
                .andExpect(status().isNotFound());
    }
}
