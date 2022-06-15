package org.gridsuite.study.server;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.ResourceUtils;

@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@ContextHierarchy({@ContextConfiguration(classes = {StudyApplication.class, TestChannelBinderConfiguration.class})})
public class TestTempDir {

    private static final String TEST_FILE = "testCase.xiidm";
    private static final String STUDIES_URL = "/v1/studies";

    private static final long TIMEOUT = 1000;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutputDestination output;

    @SpyBean
    private TempFileService tempFileService;

    @Test
    public void testCreateTmpDirException() throws Exception {
        doThrow(new IOException("Error when creating temp file")).when(tempFileService).createTempFile(anyString(), anyString(), any());

        try (InputStream is = new FileInputStream(ResourceUtils.getFile("classpath:" + TEST_FILE))) {
            MockMultipartFile mockFile = new MockMultipartFile("caseFile", TEST_FILE, "text/xml", is);

            mockMvc
                .perform(multipart(STUDIES_URL + "?isPrivate={isPrivate}", true).file(mockFile)
                        .header("userId", "userId").contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().is5xxServerError());
        }

     // assert that the broker message has been sent a study creation request message
        output.receive(TIMEOUT);
     // assert that the broker message has been sent a study creation request deletion message
        output.receive(TIMEOUT);
    }
}
