/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.client;

import org.gridsuite.study.server.StudyException;

import java.net.URI;
import java.net.URISyntaxException;

import static org.gridsuite.study.server.StudyException.Type.URI_SYNTAX;
import static org.gridsuite.study.server.service.client.RestClient.DELIMITER;

public final class UrlUtil {
    private UrlUtil() {

    }

    public static String buildEndPointUrl(String apiVersion, String endPoint) {
        try {
            String url = new StringBuilder()
                    .append(DELIMITER)
                    .append(apiVersion)
                    .append(DELIMITER)
                    .append(endPoint)
                    .append(DELIMITER)
                    .toString();
            // normalize before return
            return new URI(url).normalize().toString();
        } catch (URISyntaxException e) {
            throw new StudyException(URI_SYNTAX, e.getMessage());
        }
    }
}
