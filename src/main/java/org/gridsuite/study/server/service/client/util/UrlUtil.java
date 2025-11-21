/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.client.util;

import org.apache.logging.log4j.util.Strings;

import java.net.URI;
import java.net.URISyntaxException;

import static org.gridsuite.study.server.service.client.RestClient.DELIMITER;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
public final class UrlUtil {
    private UrlUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Build endpoint url
     * @param baseUri base uri with "http://domain:port" or empty
     * @param apiVersion for example "v1" or empty
     * @param endPoint root endpoint
     * @return a normalized completed url to endpoint
     */
    public static String buildEndPointUrl(String baseUri, String apiVersion, String endPoint) {
        try {
            var sb = new StringBuilder(baseUri);
            if (Strings.isNotBlank(apiVersion)) {
                sb.append(DELIMITER).append(apiVersion);
            }
            if (Strings.isNotBlank(endPoint)) {
                sb.append(DELIMITER).append(endPoint);
            }
            var url = sb.toString();

            // normalize before return
            return new URI(url).normalize().toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("impossible to build url", e);
        }
    }
}
