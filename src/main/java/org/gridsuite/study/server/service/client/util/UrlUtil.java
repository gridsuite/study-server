/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.client.util;

import org.apache.logging.log4j.util.Strings;
import org.gridsuite.study.server.StudyException;

import java.net.URI;
import java.net.URISyntaxException;

import static org.gridsuite.study.server.StudyException.Type.URI_SYNTAX;
import static org.gridsuite.study.server.service.client.RestClient.DELIMITER;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
public final class UrlUtil {
    private UrlUtil() {

    }

    /**
     * Build endpoint url
     * @param baseUri base uri with http://domain:port or empty
     * @param apiVersion for example "v1" or empty
     * @param endPoint root endpoint
     * @return a normalized completed url to endpoint
     */
    public static String buildEndPointUrl(String baseUri, String apiVersion, String endPoint) {
        try {
            var sb = new StringBuilder(baseUri);
            sb.append(DELIMITER);
            if (Strings.isNotBlank(apiVersion)) {
                sb.append(apiVersion).append(DELIMITER);
            }
            if (Strings.isNotBlank(endPoint)) {
                sb.append(endPoint).append(DELIMITER);
            }
            var url = sb.toString();

            // normalize before return
            return new URI(url).normalize().toString();
        } catch (URISyntaxException e) {
            throw new StudyException(URI_SYNTAX, e.getMessage());
        }
    }
}
