package com.example.floodmonitor.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI-Beschreibung, abrufbar unter /v3/api-docs (JSON), /v3/api-docs.yaml und /swagger-ui.html.
 */
@Configuration
public class OpenApiConfig {

    public static final String API_KEY_SCHEME = "apiKey";

    @Bean
    public OpenAPI floodMonitorOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Flood Monitor API")
                        .version("v1")
                        .description("""
                                REST-Schnittstelle eines Hochwasser-Frühwarnsystems.

                                **Formate:** Alle Endpunkte liefern JSON (Standard) oder XML. Die Auswahl erfolgt über \
                                den `Accept`-Header (`application/json`, `application/xml`) oder durch Anhängen von `/xml` \
                                an die URL. Andere Formate → `406 Not Acceptable`.

                                **Authentifizierung:** Lesende Endpunkte (GET) sind öffentlich. Schreibende Endpunkte \
                                (POST, DELETE) benötigen den Header `X-API-Key`. Fehlt er → `401`, ist er falsch → `403`.

                                **Fehler** werden einheitlich als `ApiError` (JSON oder XML) geliefert, ohne Stacktrace."""))
                .components(new Components().addSecuritySchemes(API_KEY_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(ApiKeyInterceptor.HEADER)
                        .description("API-Key für schreibende Zugriffe (Property api.key)")));
    }
}
