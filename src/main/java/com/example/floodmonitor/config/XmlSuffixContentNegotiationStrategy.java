package com.example.floodmonitor.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.accept.ContentNegotiationStrategy;
import org.springframework.web.context.request.NativeWebRequest;

import java.util.List;

/**
 * Endet die URL auf {@code /xml}, wird XML geliefert (z. B. {@code /api/v1/stations/ST-001/xml}).
 * Sonst entscheiden die nachfolgenden Strategien (Accept-Header, Standard JSON).
 */
public class XmlSuffixContentNegotiationStrategy implements ContentNegotiationStrategy {

    public static final String SUFFIX = "/xml";

    @Override
    public List<MediaType> resolveMediaTypes(NativeWebRequest webRequest) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request != null && request.getRequestURI().endsWith(SUFFIX)) {
            return List.of(MediaType.APPLICATION_XML);
        }
        return MEDIA_TYPE_ALL_LIST;
    }
}
