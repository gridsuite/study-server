package org.gridsuite.study.server;

import org.gridsuite.study.server.service.LoadFlowService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ContextConfigurationWithTestChannel
public class NetworkMapControllerTest {

    @MockitoBean
    private LoadFlowService loadFlowService;
}
