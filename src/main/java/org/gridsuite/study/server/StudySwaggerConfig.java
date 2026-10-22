/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import org.gridsuite.study.server.utils.annotations.ExternalContract;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

import java.util.List;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@Configuration
public class StudySwaggerConfig {

    /**
     * OpenAPI extension key under which the consumers of an {@link ExternalContract}-annotated
     * endpoint are exposed in the generated OpenAPI document.
     */
    public static final String X_EXTERNAL_CONSUMERS_EXTENSION = "x-external-consumers";

    @Bean
    public OpenAPI createOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Study API")
                        .description("This is the documentation of the Study REST API")
                        .version(StudyApi.API_VERSION));
    }

    /**
     * Surfaces endpoints annotated with {@link ExternalContract} in the generated OpenAPI document,
     * so that external consumers (and the {@code ExternalContractTest}) can identify them.
     */
    @Bean
    public OperationCustomizer externalContractOperationCustomizer() {
        return (Operation operation, HandlerMethod handlerMethod) -> {
            ExternalContract externalContract = handlerMethod.getMethodAnnotation(ExternalContract.class);
            if (externalContract != null) {
                operation.addExtension(X_EXTERNAL_CONSUMERS_EXTENSION, List.of(externalContract.consumers()));
            }
            return operation;
        };
    }
}
