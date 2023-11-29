/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.config;

import org.gridsuite.study.server.notification.NotificationService;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@MockBean(NotificationService.class)
@TestPropertySource(properties = DisableCloudStream.DISABLE_PROPERTY_NAME + "=true")
public @interface DisableCloudStream {
    String DISABLE_PROPERTY_NAME = "test.disable.cloud-stream";
}
