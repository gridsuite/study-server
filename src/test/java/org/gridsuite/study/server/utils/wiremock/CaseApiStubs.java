package org.gridsuite.study.server.utils.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.gridsuite.study.server.utils.wiremock.WireMockUtils.*;

public class CaseApiStubs {
    private final WireMockServer wireMock;
    private final String CASE_URI = "/v1/cases";

    public CaseApiStubs(WireMockServer wireMock) {
        this.wireMock = wireMock;
    }

    public UUID stubCaseExists(String caseUuid, boolean returnedValue) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo(CASE_URI + "/" + caseUuid + "/exists"))
            .willReturn(WireMock.ok().withBody(returnedValue ? "true" : "false")
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        ).getId();
    }

    public void verifyCaseExists(UUID stubUuid, String caseUuid) {
        RequestPatternBuilder requestBuilder = WireMock.getRequestedFor(WireMock.urlPathEqualTo(CASE_URI  + "/" + caseUuid + "/exists"));
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(wireMock, stubUuid, 1);
    }

    public UUID stubDisableCaseExpiration(String caseUuid) {
        return wireMock.stubFor(WireMock.put(WireMock.urlPathEqualTo(CASE_URI + "/" + caseUuid + "/disableExpiration"))
            .willReturn(WireMock.ok())).getId();
    }

    public void verifyDisableCaseExpiration(UUID stubUuid, String caseUuid) {
        verifyPutRequest(wireMock, stubUuid, CASE_URI + "/" + caseUuid + "/disableExpiration", false, Map.of(), null);
    }

    public UUID stubDuplicateCase(String caseUuid) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo(CASE_URI))
                .withQueryParam("withExpiration", WireMock.matching(".*"))
                .withQueryParam("duplicateFrom", WireMock.equalTo(caseUuid))
            .willReturn(WireMock.ok())).getId();
    }

    public void verifyDuplicateCase(UUID stubUuid, String caseUuid) {
        HashMap<String, StringValuePattern> params = new HashMap<>();
        params.put("withExpiration", WireMock.matching(".*"));
        params.put("duplicateFrom", WireMock.equalTo(caseUuid));
        verifyPostRequest(wireMock, stubUuid, CASE_URI, params);
    }
}
