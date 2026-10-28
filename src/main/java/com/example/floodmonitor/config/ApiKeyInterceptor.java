package com.example.floodmonitor.config;

import com.example.floodmonitor.exception.ForbiddenException;
import com.example.floodmonitor.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/**
 * Lesende Anfragen (GET, HEAD, OPTIONS) sind öffentlich. Schreibende Anfragen (POST, PUT, PATCH, DELETE)
 * brauchen den Header {@value #HEADER}: fehlt er → 401, ist er falsch → 403.
 */
public class ApiKeyInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-API-Key";
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final byte[] apiKey;

    public ApiKeyInterceptor(String apiKey) {
        this.apiKey = apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (READ_METHODS.contains(request.getMethod())) {
            return true;
        }
        String provided = request.getHeader(HEADER);
        if (provided == null || provided.isBlank()) {
            throw new UnauthorizedException("Für schreibende Zugriffe ist der Header " + HEADER + " erforderlich");
        }
        // Vergleich in konstanter Zeit, damit der Key nicht über Antwortzeiten erraten werden kann
        if (!MessageDigest.isEqual(apiKey, provided.getBytes(StandardCharsets.UTF_8))) {
            throw new ForbiddenException("Ungültiger API-Key");
        }
        return true;
    }
}
