/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller;

import org.gridsuite.study.server.service.DirectoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DirectoryControllerTest {

    private static final String BASE_URL = "/v1/directory";
    private static final String USER_ID = "userId";

    @Mock
    private DirectoryService directoryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DirectoryController(directoryService)).build();
    }

    @Test
    void testGetElements() throws Exception {
        UUID elementUuid = UUID.randomUUID();
        String elements = "[{\"id\":\"" + elementUuid + "\"}]";
        when(directoryService.getElements(List.of(elementUuid), List.of("STUDY"), false, USER_ID)).thenReturn(elements);

        mockMvc.perform(get(BASE_URL + "/elements")
                .param("ids", elementUuid.toString())
                .param("elementTypes", "STUDY")
                .param("strictMode", "false")
                .header("userId", USER_ID))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(elements));

        verify(directoryService).getElements(List.of(elementUuid), List.of("STUDY"), false, USER_ID);
    }

    @Test
    void testElementExists() throws Exception {
        UUID directoryUuid = UUID.randomUUID();
        when(directoryService.elementExists(directoryUuid, "elementName", "STUDY")).thenReturn(true);

        mockMvc.perform(head(BASE_URL + "/directories/{directoryUuid}/elements/{elementName}/types/{type}", directoryUuid, "elementName", "STUDY"))
            .andExpect(status().isOk())
            .andExpect(content().string(""));

        verify(directoryService).elementExists(directoryUuid, "elementName", "STUDY");
    }

    @Test
    void testElementDoesNotExist() throws Exception {
        UUID directoryUuid = UUID.randomUUID();
        when(directoryService.elementExists(directoryUuid, "elementName", "STUDY")).thenReturn(false);

        mockMvc.perform(head(BASE_URL + "/directories/{directoryUuid}/elements/{elementName}/types/{type}", directoryUuid, "elementName", "STUDY"))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

