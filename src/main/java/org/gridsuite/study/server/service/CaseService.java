/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */

import static org.gridsuite.study.server.StudyConstants.CASE_API_VERSION;
import static org.gridsuite.study.server.StudyConstants.DELIMITER;
import static org.gridsuite.study.server.StudyException.Type.CASE_NOT_FOUND;
import static org.gridsuite.study.server.StudyException.Type.STUDY_CREATION_FAILED;

import java.util.UUID;

import org.gridsuite.study.server.StudyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class CaseService {

    private String caseServerBaseUri;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    public CaseService(@Value("${backing-services.case.base-uri:http://case-server/}") String caseServerBaseUri) {
        this.caseServerBaseUri = caseServerBaseUri;
    }

    public String getCaseName(UUID caseUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + CASE_API_VERSION + "/cases/{caseUuid}/name")
                .buildAndExpand(caseUuid)
                .toUriString();

        try {
            return restTemplate.exchange(caseServerBaseUri + path, HttpMethod.GET, null, String.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw new StudyException(CASE_NOT_FOUND, e.getMessage());
        }
    }

    public String getCaseFormat(UUID caseUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + CASE_API_VERSION + "/cases/{caseUuid}/format")
            .buildAndExpand(caseUuid)
            .toUriString();

        return restTemplate.getForObject(caseServerBaseUri + path, String.class);
    }

    UUID importCase(MultipartFile multipartFile) {
        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        UUID caseUuid;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        try {
            multipartBodyBuilder
                .part("file", multipartFile.getBytes()).filename(multipartFile.getOriginalFilename());

            HttpEntity<MultiValueMap<String, HttpEntity<?>>> request = new HttpEntity<>(
                    multipartBodyBuilder.build(), headers);

            try {
                caseUuid = restTemplate.postForObject(caseServerBaseUri + "/" + CASE_API_VERSION + "/cases/private",
                        request, UUID.class);
            } catch (HttpStatusCodeException e) {
                throw buildExceptionFromHttpException(e);
            }
        } catch (StudyException e) {
            throw e;
        } catch (Exception e) {
            throw new StudyException(STUDY_CREATION_FAILED, e.getMessage());
        }

        return caseUuid;
    }

    public Boolean caseExists(UUID caseUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + CASE_API_VERSION + "/cases/{caseUuid}/exists")
            .buildAndExpand(caseUuid)
            .toUriString();

        return restTemplate.exchange(caseServerBaseUri + path, HttpMethod.GET, null, Boolean.class, caseUuid).getBody();
    }

    private StudyException buildExceptionFromHttpException(HttpStatusCodeException httpException) {
        HttpStatus httpStatusCode = httpException.getStatusCode();
        String errorMessage = httpException.getResponseBodyAsString();
        String errorToParse = errorMessage.isEmpty() ? "{\"message\": \"case-server: " + httpStatusCode + "\"}"
                : errorMessage;

        try {
            JsonNode node = new ObjectMapper().readTree(errorToParse).path("message");
            if (!node.isMissingNode()) {
                return new StudyException(STUDY_CREATION_FAILED, node.asText());
            }
        } catch (JsonProcessingException e) {
            if (!errorToParse.isEmpty()) {
                return new StudyException(STUDY_CREATION_FAILED, errorToParse);
            }
        }

        return new StudyException(STUDY_CREATION_FAILED, errorToParse);
    }

    public void assertCaseExists(UUID caseUuid) {
        if (Boolean.FALSE.equals(caseExists(caseUuid))) {
            throw new StudyException(CASE_NOT_FOUND, "The case '" + caseUuid + "' does not exist");
        }
    }

    public void setCaseServerBaseUri(String caseServerBaseUri) {
        this.caseServerBaseUri = caseServerBaseUri;
    }
}
