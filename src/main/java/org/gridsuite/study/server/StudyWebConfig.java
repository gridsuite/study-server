/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AllArgsConstructor
@Configuration
public class StudyWebConfig implements WebMvcConfigurer {
    private InsensitiveStringToEnumConverterFactory insensitiveEnumConverterFactory;
    private StudyAccessInterceptor studyAccessInterceptor;

    /**
     * {@inheritDoc}
     */
    @Override
    public void addFormatters(final FormatterRegistry registry) {
        registry.addConverterFactory(insensitiveEnumConverterFactory);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(studyAccessInterceptor)
            .addPathPatterns("/" + StudyApi.API_VERSION + "/studies/{studyUuid}",
                "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/**");
    }
}
