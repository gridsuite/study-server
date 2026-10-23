/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a REST controller endpoint as part of a contract relied upon by one or more external
 * applications (i.e. applications that are not part of this repository, such as external-app-name).
 *
 * <p>Endpoints annotated with {@code @ExternalContract} must be changed with extra care: a
 * breaking change on their path, HTTP method, parameters or response/request schema can break the
 * consuming application(s) in production.
 *
 * <p>The annotation is surfaced in the generated OpenAPI document (as the {@code x-external-consumers}
 * extension, see {@code ExternalContractOpenApiCustomizer}) and is used by the
 * {@code ExternalContractTest} to detect unintended breaking changes against the committed
 * {@code src/test/resources/external-contract/reference.json} snapshot.
 *
 * @author Abdellah Ghiles <abdellah.ghiles at rte-france.com>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExternalContract {
    /**
     * Names of the external applications known to rely on this endpoint (e.g. {@code "external-app-name"}).
     */
    String[] consumers();

    /**
     * Optional free-text note (e.g. link to a ticket, contact point, or migration plan) to help
     * whoever needs to change this endpoint anticipate the impact on the listed consumers.
     */
    String note() default "";
}
