/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.externalcontract.ExternalContractComparator;
import org.gridsuite.study.server.externalcontract.ExternalContractExtractor;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards endpoints tagged with {@code @ExternalContract} (i.e. relied upon by external
 * applications such as external-app-name) against unintended breaking changes.
 *
 * <p>This test generates the current contract snapshot of every {@code @ExternalContract}
 * endpoint from the live OpenAPI document ({@code /v3/api-docs}) and compares it against the
 * committed {@code src/test/resources/external-contract/reference.json} snapshot. The reference
 * file is a <strong>subset</strong> of the current endpoints: only the endpoints it lists are
 * checked.
 *
 * <p>If this test fails, it means a change in this PR impacts an endpoint used by an external
 * application. Either:
 * <ul>
 *     <li>the change was unintentional: fix the code so the contract is preserved, or</li>
 *     <li>the change is intentional and was coordinated with the consumer(s) listed in
 *     {@code reference.json}: regenerate the reference file (see
 *     {@code ExternalContractReferenceGenerator}) and get the change reviewed.</li>
 * </ul>
 *
 * @author Abdellah Ghiles <abdellah.ghiles at rte-france.com>
 */
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class ExternalContractTest {
    private static final String REFERENCE_CONTRACT_RESOURCE = "external-contract/reference.json";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void externalContractEndpointsShouldMatchReferenceSnapshot() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode openApiDocument = objectMapper.readTree(
                mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());
        Map<String, JsonNode> currentSnapshots = ExternalContractExtractor.extract(openApiDocument);

        JsonNode referenceDocument = objectMapper.readTree(new ClassPathResource(REFERENCE_CONTRACT_RESOURCE).getInputStream());
        Map<String, JsonNode> referenceSnapshots = objectMapper.convertValue(referenceDocument,
                objectMapper.getTypeFactory().constructMapType(java.util.TreeMap.class, String.class, JsonNode.class));

        List<String> violations = ExternalContractComparator.compare(referenceSnapshots, currentSnapshots);
        assertTrue(violations.isEmpty(), () -> "One or more @ExternalContract endpoints changed shape:\n\n"
                + String.join("\n\n", violations)
                + "\n\nSee docs/external-contract-guard.md for how to proceed.");
    }
}
