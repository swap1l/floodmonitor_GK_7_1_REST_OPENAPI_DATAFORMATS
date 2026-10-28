package com.example.floodmonitor;

import com.example.floodmonitor.model.Measurement;
import com.example.floodmonitor.model.Station;
import com.example.floodmonitor.model.StationStatus;
import com.example.floodmonitor.repository.MeasurementRepository;
import com.example.floodmonitor.repository.StationRepository;
import com.example.floodmonitor.service.MeasurementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

@SpringBootTest
@AutoConfigureMockMvc
class FloodMonitorApiTest {

    // ST-001: warning 450 cm, critical 600 cm
    private final Instant NOW =Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Autowired MockMvc mvc;
    @Autowired StationRepository stationRepository;
    @Autowired MeasurementRepository measurementRepository;
    @Autowired MeasurementService measurementService;

    @BeforeEach
    void setUp() {
        measurementRepository.deleteAll();
        Station st1 = stationRepository.findById("ST-001").orElseThrow();
        add(st1, NOW.minus(3, ChronoUnit.HOURS), 300);
        add(st1, NOW.minus(2, ChronoUnit.HOURS), 460);
        add(st1, NOW.minus(1, ChronoUnit.HOURS), 610);
        add(st1, NOW, 320);
        Station st2 = stationRepository.findById("ST-002").orElseThrow();
        add(st2, NOW, 520);
    }

    private void add(Station station, Instant timestamp, double waterLevel) {
        Measurement previous = measurementRepository.findLatest(station.getId()).orElse(null);
        measurementService.addMeasurement(station,
                new Measurement(station.getId(), timestamp, waterLevel, 1000, 0, 12, 80, StationStatus.ONLINE),
                previous);
    }

    @Test
    void stationsAsJsonByDefault() throws Exception {
        mvc.perform(get("/api/v1/stations"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].id").value("ST-001"))
                .andExpect(jsonPath("$[0].isActive").value(true))
                .andExpect(jsonPath("$[0].location.latitude").exists());
    }

    @Test
    void stationsAsXml() throws Exception {
        mvc.perform(get("/api/v1/stations").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(xpath("count(/List/item)").number(5.0))
                .andExpect(xpath("/List/item[1]/id").string("ST-001"));
    }

    @Test
    void xmlSuffixReturnsXml() throws Exception {
        mvc.perform(get("/api/v1/stations/xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(xpath("count(/List/item)").number(5.0));
        mvc.perform(get("/api/v1/stations/ST-001/xml"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(xpath("/station/id").string("ST-001"));
        mvc.perform(get("/api/v1/stations/ST-001/measurements/latest/xml"))
                .andExpect(xpath("/measurement/waterLevel").string("320.0"));
        mvc.perform(get("/api/v1/stations/ST-001/measurements/xml").param("limit", "2"))
                .andExpect(xpath("count(/List/item)").number(2.0));
        mvc.perform(get("/api/v1/stations/ST-001/statistics/xml"))
                .andExpect(xpath("/statistics/measurementCount").string("4"));
        mvc.perform(get("/api/v1/alerts/xml"))
                .andExpect(xpath("/List/item/stationId").string("ST-002"));
    }

    @Test
    void browserGetsJsonWithoutXmlSuffix() throws Exception {
        String browserAccept = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
        mvc.perform(get("/api/v1/stations").header("Accept", browserAccept))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        mvc.perform(get("/api/v1/stations/xml").header("Accept", browserAccept))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML));
    }

    @Test
    void xmlSuffixAlsoForErrors() throws Exception {
        mvc.perform(get("/api/v1/stations/ST-999/xml"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(xpath("/error/status").string("404"));
    }

    @Test
    void stationFilters() throws Exception {
        mvc.perform(get("/api/v1/stations").param("river", "donau"))
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/v1/stations").param("isActive", "false"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("ST-005"));
        mvc.perform(get("/api/v1/stations").param("warningLevel", "WARNING"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("ST-002"));
    }

    @Test
    void existingStation() throws Exception {
        mvc.perform(get("/api/v1/stations/ST-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.river").value("Donau"))
                .andExpect(jsonPath("$.currentWarningLevel").value("NORMAL"));
    }

    @Test
    void unknownStation() throws Exception {
        mvc.perform(get("/api/v1/stations/ST-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Station ST-999 wurde nicht gefunden"))
                .andExpect(jsonPath("$.path").value("/api/v1/stations/ST-999"));
    }

    @Test
    void unknownStationAsXml() throws Exception {
        mvc.perform(get("/api/v1/stations/ST-999").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotFound())
                .andExpect(xpath("/error/status").string("404"));
    }

    @Test
    void latestMeasurement() throws Exception {
        mvc.perform(get("/api/v1/stations/ST-001/measurements/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stationId").value("ST-001"))
                .andExpect(jsonPath("$.waterLevel").value(320.0))
                .andExpect(jsonPath("$.timestamp").value(NOW.toString()))
                .andExpect(jsonPath("$.warningLevel").value("NORMAL"));
    }

    @Test
    void latestMeasurementAsXml() throws Exception {
        mvc.perform(get("/api/v1/stations/ST-001/measurements/latest").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isOk())
                .andExpect(xpath("/measurement/stationId").string("ST-001"))
                .andExpect(xpath("/measurement/timestamp").string(NOW.toString()))
                .andExpect(xpath("/measurement/warningLevel").string("NORMAL"));
    }

    @Test
    void warningLevelsAreCalculatedServerSide() throws Exception {
        mvc.perform(get("/api/v1/stations/ST-001/measurements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                // neueste zuerst
                .andExpect(jsonPath("$[0].warningLevel").value("NORMAL"))
                .andExpect(jsonPath("$[1].warningLevel").value("CRITICAL"))
                .andExpect(jsonPath("$[2].warningLevel").value("WARNING"))
                .andExpect(jsonPath("$[3].warningLevel").value("NORMAL"));
    }

    @Test
    void filterByTimeRange() throws Exception {
        mvc.perform(get("/api/v1/stations/ST-001/measurements")
                        .param("from", NOW.minus(150, ChronoUnit.MINUTES).toString())
                        .param("to", NOW.minus(30, ChronoUnit.MINUTES).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].waterLevel").value(610.0))
                .andExpect(jsonPath("$[1].waterLevel").value(460.0));
    }

    @Test
    void filterByWarningLevelAndLimit() throws Exception {
        mvc.perform(get("/api/v1/stations/ST-001/measurements").param("warningLevel", "CRITICAL"))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/v1/stations/ST-001/measurements").param("limit", "2"))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void fromAfterToIsRejected() throws Exception {
        mvc.perform(get("/api/v1/stations/ST-001/measurements")
                        .param("from", "2026-05-01T12:00:00Z")
                        .param("to", "2026-05-01T08:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void invalidDateFormatIsRejected() throws Exception {
        mvc.perform(get("/api/v1/stations/ST-001/measurements").param("from", "gestern"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", startsWith("Ungültiger Wert 'gestern'")));
    }

    @Test
    void invalidWarningLevelIsRejected() throws Exception {
        mvc.perform(get("/api/v1/stations").param("warningLevel", "PANIC"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void limitBounds() throws Exception {
        mvc.perform(get("/api/v1/stations/ST-001/measurements").param("limit", "1000"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/stations/ST-001/measurements").param("limit", "1001"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/stations/ST-001/measurements").param("limit", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedFormat() throws Exception {
        mvc.perform(get("/api/v1/stations").accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    void alerts() throws Exception {
        mvc.perform(get("/api/v1/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].stationId").value("ST-002"))
                .andExpect(jsonPath("$[0].warningLevel").value("WARNING"));
        mvc.perform(get("/api/v1/alerts").param("river", "Inn"))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/v1/alerts").param("warningLevel", "CRITICAL"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void statistics() throws Exception {
        mvc.perform(get("/api/v1/stations/ST-001/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurementCount").value(4))
                .andExpect(jsonPath("$.minWaterLevel").value(300.0))
                .andExpect(jsonPath("$.maxWaterLevel").value(610.0))
                .andExpect(jsonPath("$.avgWaterLevel").value(422.5))
                .andExpect(jsonPath("$.warningCount").value(2))
                .andExpect(jsonPath("$.criticalCount").value(1));
    }
}
