package com.farmcrop.common;

import com.farmcrop.common.exception.BusinessRuleException;
import com.farmcrop.common.exception.GlobalExceptionHandler;
import com.farmcrop.common.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Unit-level test of the error envelope using a standalone MockMvc and a throwaway controller. */
class GlobalExceptionHandlerTest {

    record SampleRequest(@NotBlank(message = "Name is required.") String name) {
    }

    @RestController
    static class SampleController {
        @GetMapping("/missing")
        void missing() {
            throw new ResourceNotFoundException("Crop listing was not found.");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new BusinessRuleException("INVALID_STATE", "This application is no longer available for review.");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("database password=secret leaked in message");
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody SampleRequest request) {
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SampleController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void notFoundUsesEnvelope() throws Exception {
        mockMvc.perform(get("/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Crop listing was not found."));
    }

    @Test
    void businessRuleViolationIsConflict() throws Exception {
        mockMvc.perform(get("/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void unexpectedErrorsDoNotLeakInternalMessages() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("secret"))));
    }

    @Test
    void validationErrorsListFields() throws Exception {
        mockMvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("Name is required."));
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mockMvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
