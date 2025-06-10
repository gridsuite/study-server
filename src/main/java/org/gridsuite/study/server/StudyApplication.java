/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@SpringBootApplication
public class StudyApplication {
    public static void main(String[] args) {
        SpringApplication.run(StudyApplication.class, args);
    }
    
//    @Bean
//    public HttpClient httpClient() {
//        PoolingHttpClientConnectionManager connectionManager = 
//            new PoolingHttpClientConnectionManager();
//        connectionManager.setMaxTotal(500);
//        connectionManager.setDefaultMaxPerRoute(200);
////        RequestConfig requestConfig = RequestConfig.custom()
////            .setConnectionRequestTimeout(Timeout.ofMilliseconds(properties.getConnectionRequestTimeout()))
////            .setConnectTimeout(Timeout.ofMilliseconds(properties.getConnectTimeout()))
////            .setResponseTimeout(Timeout.ofMilliseconds(properties.getResponseTimeout()))
////            .build();
// 
//        return HttpClients.custom()
//            .setConnectionManager(connectionManager)
////            .setDefaultRequestConfig(requestConfig)
//            .build();
////    }
//// 
//    @Bean
//    public RestTemplate restTemplate(HttpClient httpClient) {
//        HttpComponentsClientHttpRequestFactory factory = 
//            new HttpComponentsClientHttpRequestFactory(httpClient);
//        return new RestTemplate(factory);
//    }    
////    @Bean
//    Consumer<PoolingHttpClientConnectionManagerBuilder> hcclient5PoolCustomizer() {
//        System.out.println("jon");
//        return (pooling) -> {
//            System.out.println("jon2");
//            pooling.setMaxConnPerRoute(200);
//            pooling.setMaxConnTotal(500);
//        };
//    }
}
