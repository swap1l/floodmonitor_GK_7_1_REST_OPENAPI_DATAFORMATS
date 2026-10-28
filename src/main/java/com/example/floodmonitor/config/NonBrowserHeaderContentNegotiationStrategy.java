package com.example.floodmonitor.config;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.accept.HeaderContentNegotiationStrategy;
import org.springframework.web.context.request.NativeWebRequest;

import java.util.List;

/**
 * Wertet den Accept-Header aus, außer bei Browser-Aufrufen (Accept enthält {@code text/html}).
 * Browser schicken {@code application/xml;q=0.9} mit und würden sonst XML statt JSON bekommen.
 */
public class NonBrowserHeaderContentNegotiationStrategy extends HeaderContentNegotiationStrategy {

    @Override
    public List<MediaType> resolveMediaTypes(NativeWebRequest request) throws HttpMediaTypeNotAcceptableException {
        String[] accept = request.getHeaderValues(HttpHeaders.ACCEPT);
        if (accept != null && String.join(",", accept).contains(MediaType.TEXT_HTML_VALUE)) {
            return MEDIA_TYPE_ALL_LIST;
        }
        return super.resolveMediaTypes(request);
    }
}
