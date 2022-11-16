/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.powsybl.contingency.json.ContingencyJsonModule;
import com.powsybl.loadflow.json.LoadFlowParametersJsonModule;
import com.powsybl.loadflow.json.LoadFlowResultJsonModule;
import com.powsybl.security.json.SecurityAnalysisJsonModule;
import com.powsybl.shortcircuit.json.ShortCircuitAnalysisJsonModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        final RestTemplate restTemplate = new RestTemplate();

        //find and replace Jackson message converter with our own
        for (int i = 0; i < restTemplate.getMessageConverters().size(); i++) {
            final HttpMessageConverter<?> httpMessageConverter = restTemplate.getMessageConverters().get(i);
            if (httpMessageConverter instanceof MappingJackson2HttpMessageConverter) {
                restTemplate.getMessageConverters().set(i, mappingJackson2HttpMessageConverter());
            }
        }

        return restTemplate;
    }

    public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper());
        return converter;
    }

    private ObjectMapper createObjectMapper() {
        var objectMapper = Jackson2ObjectMapperBuilder.json().build();
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.registerModule(new ContingencyJsonModule());
        objectMapper.registerModule(new LoadFlowResultJsonModule());
        objectMapper.registerModule(new LoadFlowParametersJsonModule());
        objectMapper.registerModule(new SecurityAnalysisJsonModule());
        objectMapper.registerModule(new ShortCircuitAnalysisJsonModule());
        return objectMapper;
    }

    @Bean
    public ObjectMapper objectMapper() {
        return createObjectMapper();
    }

}
