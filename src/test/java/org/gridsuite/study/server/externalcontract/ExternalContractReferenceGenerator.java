/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.externalcontract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.ContextConfigurationWithTestChannel;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regenerates {@code src/test/resources/external-contract/reference.json} from the current state
 * of every {@code @ExternalContract}-annotated endpoint.
 *
 * <p>This is deliberately <strong>not</strong> run as part of the normal test suite: it must only
 * be run on purpose, after intentionally changing (or adding) an {@code @ExternalContract}
 * endpoint and coordinating the change with its consumer(s). Review the resulting git diff of
 * {@code reference.json} carefully before committing it.
 *
 * <p>Run it with:
 * <pre>
 * mvn test -Dtest=ExternalContractReferenceGenerator -DgenerateExternalContractReference=true -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 *
 * @author Abdellah Ghiles <abdellah.ghiles at rte-france.com>
 */
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class ExternalContractReferenceGenerator {
    private static final String REFERENCE_CONTRACT_PATH = "src/test/resources/external-contract/reference.json";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void generateReferenceSnapshot() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("generateExternalContractReference"),
                "Skipped: run with -DgenerateExternalContractReference=true to (re)generate reference.json");

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode openApiDocument = objectMapper.readTree(
                mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());
        Map<String, JsonNode> snapshots = ExternalContractExtractor.extract(openApiDocument);

        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(new File(REFERENCE_CONTRACT_PATH), snapshots);
    }
}
