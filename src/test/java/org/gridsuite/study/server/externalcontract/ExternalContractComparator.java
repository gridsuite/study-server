/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.externalcontract;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Compares a committed "reference" external contract snapshot (see
 * {@code src/test/resources/external-contract/reference.json}) against the "current" snapshot
 * extracted from the live OpenAPI document, and reports any endpoint that is missing or whose
 * shape changed.
 *
 * <p>The reference snapshot is treated as a <strong>subset</strong> of the current one: endpoints
 * present in the current snapshot but not in the reference are ignored (they are simply not yet
 * tagged, or not sensitive).
 *
 * @author Abdellah Ghiles <abdellah.ghiles at rte-france.com>
 */
public final class ExternalContractComparator {

    private ExternalContractComparator() {
    }

    public static List<String> compare(Map<String, JsonNode> reference, Map<String, JsonNode> current) {
        List<String> violations = new ArrayList<>();
        reference.forEach((endpoint, referenceSnapshot) -> {
            JsonNode currentSnapshot = current.get(endpoint);
            if (currentSnapshot == null) {
                violations.add("Endpoint '" + endpoint + "' is tagged @ExternalContract in reference.json but is no longer exposed "
                        + "(removed, path/method changed, or @ExternalContract annotation removed).");
            } else if (!currentSnapshot.equals(referenceSnapshot)) {
                violations.add("Endpoint '" + endpoint + "' changed shape compared to the committed reference contract.\n"
                        + "  reference: " + referenceSnapshot + "\n"
                        + "  current:   " + currentSnapshot);
            }
        });
        return violations;
    }
}
