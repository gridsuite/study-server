/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.databind.JsonNode;
import org.gridsuite.study.server.config.DisableCloudStream;
import org.gridsuite.study.server.config.DisableJpa;
import org.gridsuite.study.server.dto.AboutInfo;
import org.gridsuite.study.server.dto.ServiceStatusInfos;
import org.gridsuite.study.server.dto.ServiceStatusInfos.ServiceStatus;
import org.gridsuite.study.server.exception.PartialResultException;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRequestRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkNodeInfoRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRepository;
import org.gridsuite.study.server.service.FrontService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.RemoteServicesInspector;
import org.gridsuite.study.server.service.RootNetworkService;
import org.gridsuite.study.server.service.client.RemoteServiceName;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */
@AutoConfigureMockMvc
@DisableElasticsearch
@DisableCloudStream
@DisableJpa
@MockBean(NetworkModificationTreeService.class) //strange error during bean initialization
@MockBean(RootNetworkService.class)
@MockBean(RootNetworkRepository.class)
@MockBean(RootNetworkNodeInfoRepository.class)
@MockBean(RootNetworkRequestRepository.class)
@SpringBootTest(classes = StudyApplication.class)
@ExtendWith({MockitoExtension.class})
@TestMethodOrder(MethodOrderer.MethodName.class)
class RemoteServicesInspectorEndpointTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RemoteServicesProperties remoteServicesProperties;

    @MockBean
    private RemoteServicesInspector remoteServicesInspector;

    @Test
    void testActuatorHealthUp() throws Exception {
        // select 3 services to be optional
        final List<String> optionalServices = List.of(
                RemoteServiceName.LOADFLOW_SERVER.serviceName(),
                RemoteServiceName.SECURITY_ANALYSIS_SERVER.serviceName(),
                RemoteServiceName.VOLTAGE_INIT_SERVER.serviceName());
        remoteServicesProperties.getServices().forEach(s -> s.setOptional(optionalServices.contains(s.getName())));

        // any optional service will be mocked as UP
        Mockito.when(remoteServicesInspector.getOptionalServices()).thenReturn(List.of(
            new ServiceStatusInfos(RemoteServiceName.LOADFLOW_SERVER, ServiceStatus.UP),
            new ServiceStatusInfos(RemoteServiceName.SECURITY_ANALYSIS_SERVER, ServiceStatus.UP),
            new ServiceStatusInfos(RemoteServiceName.VOLTAGE_INIT_SERVER, ServiceStatus.UP)
        ));

        // all services are supposed to be Up
        mockMvc.perform(get("/v1/optional-services"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("[{\"name\":\"loadflow-server\",\"status\":\"UP\"},{\"name\":\"security-analysis-server\",\"status\":\"UP\"},{\"name\":\"voltage-init-server\",\"status\":\"UP\"}]", true));
        Mockito.verify(remoteServicesInspector, Mockito.times(1)).getOptionalServices();
    }

    @Test
    void testActuatorHealthDown() throws Exception {
        //getActuatorHealthAllDown("{\"status\":\"DOWN\"}");
        // select 2 services to be optional
        final List<String> optionalServices = List.of(
                RemoteServiceName.SENSITIVITY_ANALYSIS_SERVER.serviceName(),
                RemoteServiceName.SHORTCIRCUIT_SERVER.serviceName(),
                RemoteServiceName.STATE_ESTIMATION_SERVER.serviceName());
        remoteServicesProperties.getServices().forEach(s -> s.setOptional(optionalServices.contains(s.getName())));

        Mockito.when(remoteServicesInspector.getOptionalServices()).thenReturn(List.of(
                new ServiceStatusInfos(RemoteServiceName.SENSITIVITY_ANALYSIS_SERVER, ServiceStatus.DOWN),
                new ServiceStatusInfos(RemoteServiceName.SHORTCIRCUIT_SERVER, ServiceStatus.DOWN),
                new ServiceStatusInfos(RemoteServiceName.STATE_ESTIMATION_SERVER, ServiceStatus.DOWN)));

        // all services are supposed to be Down
        mockMvc.perform(get("/v1/optional-services"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("[{\"name\":\"sensitivity-analysis-server\",\"status\":\"DOWN\"},{\"name\":\"shortcircuit-server\",\"status\":\"DOWN\"},{\"name\":\"state-estimation-server\",\"status\":\"DOWN\"}]", true));
        Mockito.verify(remoteServicesInspector, Mockito.times(1)).getOptionalServices();
    }

    @Test
    void testServiceIsCalledWhenRequestingServicesInfoEndpoint() throws Exception {
        final Map<String, JsonNode> returnResult = new HashMap<>(0);
        Mockito.doReturn(returnResult).when(remoteServicesInspector).getServicesInfo(Mockito.nullable(FrontService.class));
        mockMvc.perform(get("/v1/servers/infos"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{}", true));
        Mockito.verify(remoteServicesInspector, Mockito.times(1)).getServicesInfo(null);
        Mockito.verifyNoMoreInteractions(remoteServicesInspector);
    }

    @Test
    void testWhenServicesInfoEndpointReturnPartialResult() throws Exception {
        final HashMap<String, JsonNode> returnResult = new HashMap<>(0);
        Mockito.doThrow(new PartialResultException(returnResult)).when(remoteServicesInspector).getServicesInfo(Mockito.nullable(FrontService.class));
        mockMvc.perform(get("/v1/servers/infos"))
                .andExpect(status().isMultiStatus())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{}", true));
        Mockito.verify(remoteServicesInspector, Mockito.times(1)).getServicesInfo(null);
        Mockito.verifyNoMoreInteractions(remoteServicesInspector);
    }

    @Test
    void testServiceIsCalledWhenRequestingServicesInfoEndpointWithFilter() throws Exception {
        final Map<String, JsonNode> returnResult = new HashMap<>(0);
        Mockito.doReturn(returnResult).when(remoteServicesInspector).getServicesInfo(Mockito.nullable(FrontService.class));
        mockMvc.perform(get("/v1/servers/infos").queryParam("view", "study"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{}", true));
        Mockito.verify(remoteServicesInspector, Mockito.times(1)).getServicesInfo(FrontService.STUDY);
        Mockito.verifyNoMoreInteractions(remoteServicesInspector);
    }

    @Test
    void testServiceIsCalledWhenRequestingServicesAboutInfosEndpoint() throws Exception {
        final Map<String, JsonNode> returnResult = new HashMap<>(0);
        Mockito.doReturn(returnResult).when(remoteServicesInspector).getServicesInfo(Mockito.nullable(FrontService.class));
        final AboutInfo[] returnConv = new AboutInfo[0];
        Mockito.doReturn(returnConv).when(remoteServicesInspector).convertServicesInfoToAboutInfo(Mockito.anyMap());
        mockMvc.perform(get("/v1/servers/about").queryParam("view", "STUDY"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("[]", true));
        final InOrder inOrder = Mockito.inOrder(remoteServicesInspector);
        inOrder.verify(remoteServicesInspector, Mockito.times(1)).getServicesInfo(FrontService.STUDY);
        inOrder.verify(remoteServicesInspector, Mockito.times(1)).convertServicesInfoToAboutInfo(returnResult);
        Mockito.verifyNoMoreInteractions(remoteServicesInspector);
    }
}
