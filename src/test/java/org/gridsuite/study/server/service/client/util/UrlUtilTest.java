/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.client.util;

import org.gridsuite.study.server.StudyException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@RunWith(JUnit4.class)
public class UrlUtilTest {

    private static final String BASE_URI = "";
    private static final String API_VERSION = "";
    private static final String END_POINT_BAD_FORMAT = "mappings? bad format";

    @Test(expected = StudyException.class)
    public void testBuildEndPointUrlBadUri() {
        UrlUtil.buildEndPointUrl(BASE_URI, API_VERSION, END_POINT_BAD_FORMAT);
    }
}
