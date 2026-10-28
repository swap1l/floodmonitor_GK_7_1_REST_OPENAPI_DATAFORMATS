package com.example.floodmonitor.config;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.web.accept.FixedContentNegotiationStrategy;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Content Negotiation, in dieser Reihenfolge:
 * <ol>
 *   <li>URL endet auf {@code /xml} → XML</li>
 *   <li>Accept-Header ({@code application/json} oder {@code application/xml}), außer bei Browser-Aufrufen</li>
 *   <li>sonst JSON</li>
 * </ol>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String apiKey;

    public WebConfig(@Value("${api.key}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ApiKeyInterceptor(apiKey)).addPathPatterns("/api/**");
    }

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.strategies(List.of(
                new XmlSuffixContentNegotiationStrategy(),
                new NonBrowserHeaderContentNegotiationStrategy(),
                // JSON bevorzugt; ALL erlaubt Endpunkte mit anderen Formaten (z. B. /v3/api-docs.yaml)
                new FixedContentNegotiationStrategy(
                        List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.ALL))));
    }

    /** XML-Antworten beginnen mit {@code <?xml version='1.0' encoding='UTF-8'?>}. */
    @Bean
    public MappingJackson2XmlHttpMessageConverter xmlHttpMessageConverter(Jackson2ObjectMapperBuilder builder) {
        XmlMapper xmlMapper = builder.createXmlMapper(true).build();
        xmlMapper.enable(ToXmlGenerator.Feature.WRITE_XML_DECLARATION);
        return new MappingJackson2XmlHttpMessageConverter(xmlMapper);
    }
}
