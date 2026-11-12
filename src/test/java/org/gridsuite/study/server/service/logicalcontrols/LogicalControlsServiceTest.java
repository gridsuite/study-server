/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.logicalcontrols;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.common.ComputationParametersService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.gridsuite.study.server.dto.ComputationType.LOGICAL_CONTROLS;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogicalControlsServiceTest {

    private static final UUID RESULT_UUID = UUID.randomUUID();
    private static final UUID NODE_UUID = UUID.randomUUID();
    private static final UUID ROOT_NETWORK_UUID = UUID.randomUUID();
    private static final UUID NETWORK_UUID = UUID.randomUUID();
    private static final String VARIANT_ID = "node_variant";
    private static final String GLOBAL_FILTERS = "{\"countryCode\":[\"FR\"]}";

    @Mock
    private StudyRepository studyRepository;
    @Mock
    private ComputationParametersService computationParametersService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @Mock
    private NetworkModificationTreeService networkModificationTreeService;
    @Mock
    private RootNetworkService rootNetworkService;
    @Mock
    private LogicalControlsRestService logicalControlsRestService;
    @Mock
    private UserAdminService userAdminService;

    private LogicalControlsService service;

    @BeforeEach
    void setUp() {
        service = new LogicalControlsService(studyRepository, computationParametersService, notificationService, rootNetworkNodeInfoService,
            logicalControlsRestService, networkModificationTreeService, userAdminService, new ObjectMapper(), rootNetworkService);
    }

    private void stubStoredResult() {
        when(rootNetworkNodeInfoService.getComputationResultUuid(NODE_UUID, ROOT_NETWORK_UUID, LOGICAL_CONTROLS)).thenReturn(RESULT_UUID);
        when(rootNetworkService.getNetworkUuid(ROOT_NETWORK_UUID)).thenReturn(NETWORK_UUID);
        when(networkModificationTreeService.getVariantId(NODE_UUID, ROOT_NETWORK_UUID)).thenReturn(VARIANT_ID);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {GLOBAL_FILTERS, "opaque malformed JSON é + & %"})
    void resultUsesStoredLogicalControlsResultNetworkAndVariantWithoutParsing(String globalFilters) {
        stubStoredResult();
        String result = "{\"balances\":{}}";
        when(logicalControlsRestService.getLogicalControlsResult(RESULT_UUID, NETWORK_UUID, VARIANT_ID, globalFilters)).thenReturn(result);

        assertThat(service.getLogicalControlsResult(NODE_UUID, ROOT_NETWORK_UUID, globalFilters)).isEqualTo(result);

        verify(logicalControlsRestService).getLogicalControlsResult(RESULT_UUID, NETWORK_UUID, VARIANT_ID, globalFilters);
        verifyNoInteractions(notificationService, studyRepository, computationParametersService, userAdminService);
    }

    @Test
    void withoutStoredResultDoesNothingElse() {
        assertThat(service.getLogicalControlsResult(NODE_UUID, ROOT_NETWORK_UUID, GLOBAL_FILTERS)).isNull();

        verify(rootNetworkNodeInfoService).getComputationResultUuid(NODE_UUID, ROOT_NETWORK_UUID, LOGICAL_CONTROLS);
        verifyNoInteractions(rootNetworkService, networkModificationTreeService, logicalControlsRestService,
            notificationService, studyRepository, computationParametersService, userAdminService);
    }

    @Test
    void remoteFailureIsPropagatedUnchanged() {
        stubStoredResult();
        HttpClientErrorException error = new HttpClientErrorException(HttpStatus.NOT_FOUND);
        when(logicalControlsRestService.getLogicalControlsResult(RESULT_UUID, NETWORK_UUID, VARIANT_ID, GLOBAL_FILTERS)).thenThrow(error);

        assertThatThrownBy(() -> service.getLogicalControlsResult(NODE_UUID, ROOT_NETWORK_UUID, GLOBAL_FILTERS)).isSameAs(error);
    }
}