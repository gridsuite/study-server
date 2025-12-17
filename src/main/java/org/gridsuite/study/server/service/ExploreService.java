package org.gridsuite.study.server.service;

import lombok.NonNull;
import lombok.Setter;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.repository.StudyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */
@Service
public class ExploreService {
    private final RestTemplate restTemplate;
    private static final String QUERY_PARAM_USER_ID = "userId";
    private static final String QUERY_PARAM_PARENT_DIRECTORY_ID = "parentDirectoryUuid";
    private static final String QUERY_PARAM_DESCRIPTION = "description";
    private static final String QUERY_PARAM_CASE_FILE = "caseFile";

    @Setter
    private String exploreServerServerBaseUri;

    @Autowired
    public ExploreService(RemoteServicesProperties remoteServicesProperties, RestTemplate restTemplate, StudyRepository studyRepository) {
        this.exploreServerServerBaseUri = remoteServicesProperties.getServiceUri("explore-server");
        this.restTemplate = restTemplate;
    }

    public void createCase(@NonNull File file, String fileName, UUID parentDirectoryUuid, String userUuid, String description) {
        // Set up headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set(QUERY_PARAM_USER_ID, userUuid); // Replace QUERY_PARAM_USER_ID with the actual header string

        // Create body
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add(QUERY_PARAM_CASE_FILE, file);
        body.add(QUERY_PARAM_DESCRIPTION, description);
        body.add(QUERY_PARAM_PARENT_DIRECTORY_ID, parentDirectoryUuid.toString()); // Replace QUERY_PARAM_PARENT_DIRECTORY_ID with the actual param key

        // Prepare the request entity
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        String path = UriComponentsBuilder.fromPath(DELIMITER + EXPLORE_API_VERSION + "/explore/cases/{caseName}")
            .buildAndExpand(fileName)
            .toUriString();

        restTemplate.exchange(
            exploreServerServerBaseUri + path,
            HttpMethod.POST,
            requestEntity,
            Void.class);
    }
}
