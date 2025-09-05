package org.gridsuite.study.server;

import org.gridsuite.study.server.service.LoadFlowService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
public class NetworkMapControllerTest {

    @MockBean
    private LoadFlowService loadFlowService;
}
