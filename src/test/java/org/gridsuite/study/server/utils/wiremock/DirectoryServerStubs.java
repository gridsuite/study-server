package org.gridsuite.study.server.utils.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.gridsuite.study.server.dto.networkexport.NodeExportInfos;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;

public class DirectoryServerStubs {
    private final WireMockServer wireMock;
    private static final String DIRECTORY_URI = "/v1/directories";

    public DirectoryServerStubs(WireMockServer wireMock) {
        this.wireMock = wireMock;
    }

    public void stubForElementExists(UUID directoryUuid, String elementName, String type, int status) {

        UriComponentsBuilder pathBuilder = UriComponentsBuilder.fromPath(DIRECTORY_URI + "/{directoryUuid}/elements/{elementName}/types/{type}");
        String path = pathBuilder.buildAndExpand(directoryUuid, elementName, type).toUriString();
        wireMock.stubFor(WireMock.head(WireMock.urlEqualTo(path))
            .withHeader("content-type", equalTo("application/json"))
            .willReturn(WireMock.aResponse().withStatus(status)));
    }

    public void verifyElementExists(UUID directoryUuid, String elementName, String type) {
        UriComponentsBuilder pathBuilder = UriComponentsBuilder.fromPath(DIRECTORY_URI + "/{directoryUuid}/elements/{elementName}/types/{type}");
        String path = pathBuilder.buildAndExpand(directoryUuid, elementName, type).toUriString();
        WireMockUtilsCriteria.verifyHeadRequest(wireMock, path, Map.of(), 1);
    }

    public void stubForCreateElement(NodeExportInfos nodeExport, String elementAttributes, String userId) {
        UriComponentsBuilder pathBuilder = UriComponentsBuilder.fromPath(DIRECTORY_URI + "/{directoryUuid}/elements");
        String path = pathBuilder.buildAndExpand(nodeExport.directoryUuid()).toUriString();

        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo(path))
            .withHeader("userId", equalTo(userId))
            .withHeader("content-type", equalTo("application/json"))
            .withRequestBody(equalTo(elementAttributes))
            .willReturn(WireMock.aResponse().withStatus(HttpStatus.OK.value())));
    }

    public void verifyCreateElement(String elementAttributes, UUID directoryUuid) {
        UriComponentsBuilder pathBuilder = UriComponentsBuilder.fromPath(DIRECTORY_URI + "/{directoryUuid}/elements");
        String path = pathBuilder.buildAndExpand(directoryUuid).toUriString();
        WireMockUtilsCriteria.verifyPostRequest(wireMock, path, true, Map.of(), elementAttributes);
    }
}
