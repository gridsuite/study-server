package org.gridsuite.study.server.utils.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.gridsuite.study.server.utils.wiremock.WireMockUtils.removeRequestForStub;
import static org.gridsuite.study.server.utils.wiremock.WireMockUtils.verifyPutRequest;

public class CaseApiStubs {
    private final WireMockServer wireMock;

    public CaseApiStubs(WireMockServer wireMock) {
        this.wireMock = wireMock;
    }

    public UUID stubCaseExists(String caseUuid, boolean returnedValue) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/cases/" + caseUuid + "/exists"))
            .willReturn(WireMock.ok().withBody(returnedValue ? "true" : "false")
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        ).getId();
    }

    public void verifyCaseExists(UUID stubUuid, String caseUuid) {
        RequestPatternBuilder requestBuilder = WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v1/cases/" + caseUuid + "/exists"));
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(wireMock, stubUuid, 1);
    }

    public UUID stubDisableCaseExpiration(String caseUuid) {
        return wireMock.stubFor(WireMock.put(WireMock.urlPathEqualTo("/v1/cases/" + caseUuid + "/disableExpiration"))
            .willReturn(WireMock.ok())).getId();
    }

    public void verifyDisableCaseExpiration(UUID stubUuid, String caseUuid) {
        verifyPutRequest(wireMock, stubUuid, "/v1/cases/" + caseUuid + "/disableExpiration", false, Map.of(), null);
    }
}
