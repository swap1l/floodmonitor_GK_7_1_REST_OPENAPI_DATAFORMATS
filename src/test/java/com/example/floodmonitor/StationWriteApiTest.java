package com.example.floodmonitor;

import com.example.floodmonitor.repository.StationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

/** Schreibende Endpunkte, API-Key, OpenAPI und Webclient. API-Key in Tests: test-key. */
@SpringBootTest
@AutoConfigureMockMvc
class StationWriteApiTest {

    private static final String KEY_HEADER = "X-API-Key";
    private static final String NEW_STATION = """
            {
              "id": "ST-100",
              "name": "Krems",
              "river": "Donau",
              "location": {"latitude": 48.41, "longitude": 15.6},
              "normalWaterLevel": 300,
              "warningWaterLevel": 480,
              "criticalWaterLevel": 620,
              "isActive": true
            }""";

    @Autowired MockMvc mvc;
    @Autowired StationRepository stationRepository;

    @AfterEach
    void cleanUp() {
        stationRepository.deleteById("ST-100");
        stationRepository.deleteById("ST-101");
    }

    @Test
    void createStationReturns201WithLocation() throws Exception {
        mvc.perform(post("/api/v1/stations").header(KEY_HEADER, "test-key")
                        .contentType(MediaType.APPLICATION_JSON).content(NEW_STATION))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/stations/ST-100")))
                .andExpect(jsonPath("$.id").value("ST-100"))
                .andExpect(jsonPath("$.currentWarningLevel").value("UNKNOWN"));
        mvc.perform(get("/api/v1/stations/ST-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Krems"));
    }

    @Test
    void createStationFromXml() throws Exception {
        String xml = """
                <station>
                  <id>ST-101</id><name>Hall</name><river>Inn</river>
                  <location><latitude>47.28</latitude><longitude>11.5</longitude></location>
                  <normalWaterLevel>200</normalWaterLevel><warningWaterLevel>340</warningWaterLevel>
                  <criticalWaterLevel>430</criticalWaterLevel><isActive>false</isActive>
                </station>""";
        mvc.perform(post("/api/v1/stations").header(KEY_HEADER, "test-key")
                        .contentType(MediaType.APPLICATION_XML).accept(MediaType.APPLICATION_XML).content(xml))
                .andExpect(status().isCreated())
                .andExpect(xpath("/station/id").string("ST-101"))
                .andExpect(xpath("/station/isActive").string("false"));
    }

    @Test
    void missingApiKeyIs401() throws Exception {
        mvc.perform(post("/api/v1/stations").contentType(MediaType.APPLICATION_JSON).content(NEW_STATION))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void wrongApiKeyIs403() throws Exception {
        mvc.perform(delete("/api/v1/stations/ST-001").header(KEY_HEADER, "falsch"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mvc.perform(get("/api/v1/stations/ST-001")).andExpect(status().isOk());
    }

    @Test
    void duplicateStationIs409() throws Exception {
        mvc.perform(post("/api/v1/stations").header(KEY_HEADER, "test-key")
                        .contentType(MediaType.APPLICATION_JSON).content(NEW_STATION.replace("ST-100", "ST-001")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Station ST-001 existiert bereits"));
    }

    @Test
    void invalidStationIs400() throws Exception {
        mvc.perform(post("/api/v1/stations").header(KEY_HEADER, "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_STATION.replace("\"criticalWaterLevel\": 620", "\"criticalWaterLevel\": 100")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/stations").header(KEY_HEADER, "test-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{kaputt"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void unsupportedMediaTypeIs415() throws Exception {
        mvc.perform(post("/api/v1/stations").header(KEY_HEADER, "test-key")
                        .contentType(MediaType.TEXT_PLAIN).content("ST-100"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void deleteStationReturns204() throws Exception {
        mvc.perform(post("/api/v1/stations").header(KEY_HEADER, "test-key")
                        .contentType(MediaType.APPLICATION_JSON).content(NEW_STATION))
                .andExpect(status().isCreated());
        mvc.perform(delete("/api/v1/stations/ST-100").header(KEY_HEADER, "test-key"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mvc.perform(get("/api/v1/stations/ST-100")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/stations/ST-100").header(KEY_HEADER, "test-key"))
                .andExpect(status().isNotFound());
    }

    @Test
    void openApiDocumentationIsAvailable() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/stations'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/stations/{stationId}/measurements'].get.parameters").isArray())
                .andExpect(jsonPath("$.components.securitySchemes.apiKey.name").value("X-API-Key"));
        mvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk());
    }

    @Test
    void webclientIsServed() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        mvc.perform(get("/webjars/jquery/3.6.1/jquery.min.js")).andExpect(status().isOk());
    }
}
