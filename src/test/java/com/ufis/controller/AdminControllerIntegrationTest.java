package com.ufis.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void seedDummyDataCreatesSmallDatasetAndReturnsSampleIds() throws Exception {
        mockMvc.perform(post("/admin/seed-dummy-data"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tier").value("SMALL"))
                .andExpect(jsonPath("$.initialEntities").value(10))
                .andExpect(jsonPath("$.initialSecurities").value(30))
                .andExpect(jsonPath("$.corporateActions").value(50))
                .andExpect(jsonPath("$.sampleEntityId").isNotEmpty())
                .andExpect(jsonPath("$.sampleSecurityId").isNotEmpty())
                .andExpect(jsonPath("$.sampleActionId").isNotEmpty())
                .andExpect(jsonPath("$.searchHint").value("Alpha"));
    }

    @Test
    void seedDummyDataCanBeCalledTwiceWithoutIdentifierCollisions() throws Exception {
        mockMvc.perform(post("/admin/seed-dummy-data"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/admin/seed-dummy-data"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sampleEntityId").isNotEmpty())
                .andExpect(jsonPath("$.sampleSecurityId").isNotEmpty())
                .andExpect(jsonPath("$.sampleActionId").isNotEmpty());
    }
}
