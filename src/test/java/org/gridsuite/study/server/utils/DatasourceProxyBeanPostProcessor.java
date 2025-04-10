/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils;

import net.ttddyy.dsproxy.listener.ChainListener;
import net.ttddyy.dsproxy.listener.DataSourceQueryCountListener;
import net.ttddyy.dsproxy.listener.logging.SLF4JLogLevel;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import javax.sql.DataSource;
import java.lang.reflect.Method;

@Component
/**
 * The author of db-utils describes its library in https://vladmihalcea.com/how-to-detect-the-n-plus-one-query-problem-during-testing/
 * But the recommended method to select the datasource (using @bean public DataSource dataSource(DataSource originalDataSource) {...} )
 * doesn't work when you use the spring default profile for tests
 * It crashes with this exception : java.lang.IllegalStateException: Failed to load ApplicationContext : org.springframework.beans.factory.BeanCurrentlyInCreationException: Error creating bean with name 'dataSource': Requested bean is currently in creation: Is there an unresolvable circular reference
 * Instead, the underlying datasource-proxy library author recommends to use a BeanPostProcessor:
 * https://github.com/ttddyy/datasource-proxy-examples/blob/master/springboot-autoconfig-example/src/main/java/net/ttddyy/dsproxy/example/DatasourceProxyBeanPostProcessor.java
 */
public class DatasourceProxyBeanPostProcessor implements BeanPostProcessor {
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource && !(bean instanceof ProxyDataSource)) {
            // Instead of directly returning a less specific datasource bean
            // (e.g.: HikariDataSource -> DataSource), return a proxy object.
            // See following links for why:
            //   https://stackoverflow.com/questions/44237787/how-to-use-user-defined-database-proxy-in-datajpatest
            //   https://gitter.im/spring-projects/spring-boot?at=5983602d2723db8d5e70a904
            //   http://blog.arnoldgalovics.com/2017/06/26/configuring-a-datasource-proxy-in-spring-boot/
            final ProxyFactory factory = new ProxyFactory(bean);
            factory.setProxyTargetClass(true);
            factory.addAdvice(new ProxyDataSourceInterceptor((DataSource) bean));
            return factory.getProxy();
        }
        return bean;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        return bean;
    }

    private static class ProxyDataSourceInterceptor implements MethodInterceptor {
        private final DataSource dataSource;

        public ProxyDataSourceInterceptor(final DataSource dataSource) {
            ChainListener listener = new ChainListener();
            listener.addListener(new DataSourceQueryCountListener());
            this.dataSource = ProxyDataSourceBuilder.create(dataSource)
                    .multiline()
                    .listener(listener)
                    .logQueryBySlf4j(SLF4JLogLevel.INFO)
                    .build();
        }

        @Override
        public Object invoke(final MethodInvocation invocation) throws Throwable {
            final Method proxyMethod = ReflectionUtils.findMethod(this.dataSource.getClass(),
                    invocation.getMethod().getName());
            if (proxyMethod != null) {
                return proxyMethod.invoke(this.dataSource, invocation.getArguments());
            }
            return invocation.proceed();
        }
    }
}
