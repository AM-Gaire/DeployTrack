package com.deploytrack.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Spring binds query parameters to enums with Enum.valueOf(), which is
// case-sensitive and knows nothing about Jackson's @JsonProperty. Without
// this, the API contradicts itself: a request body accepts {"environment":
// "staging"} while ?environment=staging fails and only ?environment=STAGING
// works. One spelling should work everywhere.
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(new CaseInsensitiveEnumConverterFactory());
    }

    static class CaseInsensitiveEnumConverterFactory implements ConverterFactory<String, Enum> {

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public <T extends Enum> Converter<String, T> getConverter(Class<T> targetType) {
            return source -> {
                String value = source.trim();
                if (value.isEmpty()) {
                    return null;
                }
                // Uppercasing covers both the wire format used in JSON
                // (lowercase environments) and the enum's own constant names,
                // so ?status=in_progress and ?status=IN_PROGRESS both bind.
                return (T) Enum.valueOf(targetType, value.toUpperCase());
            };
        }
    }
}
