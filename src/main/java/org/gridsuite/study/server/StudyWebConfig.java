package org.gridsuite.study.server;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AllArgsConstructor
@Configuration
public class StudyWebConfig implements WebMvcConfigurer {
    private InsensitiveStringToEnumConverterFactory insensitiveEnumConverterFactory;

    /**
     * {@inheritDoc}
     */
    @Override
    public void addFormatters(final FormatterRegistry registry) {
        registry.addConverterFactory(insensitiveEnumConverterFactory);
    }
}
