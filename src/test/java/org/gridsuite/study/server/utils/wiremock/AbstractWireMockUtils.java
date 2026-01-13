package org.gridsuite.study.server.utils.wiremock;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;

import java.util.Map;

/**
 * This class provides utility functions to exclude query params from URL matching if no one is provided.
 * If there are no query params, we match the complete URL, including the query string, which is empty.
 * Then we are sure to not match other requests than the one we verified.
 *
 * @author Florent MILLOT <florent.millot_externe at rte-france.com>
 */
public abstract class AbstractWireMockUtils {
    protected AbstractWireMockUtils() {
        throw new IllegalStateException("Utility class");
    }

    protected static RequestPatternBuilder postRequestBuilder(String urlPath, boolean regexMatching, Map<String, StringValuePattern> queryParams) {
        if (queryParams.isEmpty()) {
            return regexMatching
                ? WireMock.postRequestedFor(WireMock.urlMatching(AbstractWireMockUtils.noQueryUrlRegex(urlPath)))
                : WireMock.postRequestedFor(WireMock.urlEqualTo(urlPath));
        }
        return regexMatching
            ? WireMock.postRequestedFor(WireMock.urlPathMatching(urlPath))
            : WireMock.postRequestedFor(WireMock.urlPathEqualTo(urlPath));
    }

    protected static RequestPatternBuilder putRequestBuilder(String urlPath, boolean regexMatching, Map<String, StringValuePattern> queryParams) {
        if (queryParams.isEmpty()) {
            return regexMatching
                ? WireMock.putRequestedFor(WireMock.urlMatching(AbstractWireMockUtils.noQueryUrlRegex(urlPath)))
                : WireMock.putRequestedFor(WireMock.urlEqualTo(urlPath));
        }
        return regexMatching
            ? WireMock.putRequestedFor(WireMock.urlPathMatching(urlPath))
            : WireMock.putRequestedFor(WireMock.urlPathEqualTo(urlPath));
    }

    protected static RequestPatternBuilder deleteRequestBuilder(String urlPath, boolean regexMatching, Map<String, StringValuePattern> queryParams) {
        if (queryParams.isEmpty()) {
            return regexMatching
                ? WireMock.deleteRequestedFor(WireMock.urlMatching(AbstractWireMockUtils.noQueryUrlRegex(urlPath)))
                : WireMock.deleteRequestedFor(WireMock.urlEqualTo(urlPath));
        }
        return regexMatching
            ? WireMock.deleteRequestedFor(WireMock.urlPathMatching(urlPath))
            : WireMock.deleteRequestedFor(WireMock.urlPathEqualTo(urlPath));
    }

    protected static RequestPatternBuilder getRequestBuilder(String urlPathOrPattern, boolean regexMatching, Map<String, StringValuePattern> queryParams) {
        if (queryParams.isEmpty()) {
            return regexMatching
                ? WireMock.getRequestedFor(WireMock.urlMatching(AbstractWireMockUtils.noQueryUrlRegex(urlPathOrPattern)))
                : WireMock.getRequestedFor(WireMock.urlEqualTo(urlPathOrPattern));
        }
        return regexMatching
            ? WireMock.getRequestedFor(WireMock.urlPathMatching(urlPathOrPattern))
            : WireMock.getRequestedFor(WireMock.urlPathEqualTo(urlPathOrPattern));
    }

    /**
     * Builds a regex that matches the complete URL but forbids any query string.
     * - (?!.*\\?) : refuses the presence of '?'
     * - ^(?:pattern)$ : forces complete match
     */
    private static String noQueryUrlRegex(String urlPathPattern) {
        return "^(?!.*\\?)(?:" + urlPathPattern + ")$";
    }
}
