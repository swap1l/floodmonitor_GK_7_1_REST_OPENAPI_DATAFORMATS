package com.example.floodmonitor.repository;

import com.example.floodmonitor.model.Location;
import com.example.floodmonitor.model.Station;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * In-Memory-Speicher für Messstationen, sortiert nach ID.
 */
@Repository
public class StationRepository {

    private final Map<String, Station> stations = new ConcurrentSkipListMap<>();

    public StationRepository() {
        // id, name, river, location, normal, warning, critical (cm), active, mean flow (m³/s), base temperature (°C)
        save(new Station("ST-001", "Wien Reichsbrücke", "Donau", new Location(48.2289, 16.4106),
                280, 450, 600, true, 1900, 14));
        save(new Station("ST-002", "Linz", "Donau", new Location(48.3069, 14.2858),
                330, 500, 650, true, 1500, 13));
        save(new Station("ST-003", "Innsbruck", "Inn", new Location(47.2692, 11.4041),
                220, 360, 450, true, 160, 11));
        save(new Station("ST-004", "Salzburg", "Salzach", new Location(47.8095, 13.0550),
                250, 420, 550, true, 180, 12));
        save(new Station("ST-005", "Graz", "Mur", new Location(47.0707, 15.4395),
                150, 280, 380, false, 110, 13));
    }

    public List<Station> findAll() {
        return new ArrayList<>(stations.values());
    }

    public Optional<Station> findById(String id) {
        return Optional.ofNullable(stations.get(id));
    }

    public Station save(Station station) {
        stations.put(station.getId(), station);
        return station;
    }

    /** Speichert die Station nur, wenn die ID noch frei ist. */
    public boolean saveIfAbsent(Station station) {
        return stations.putIfAbsent(station.getId(), station) == null;
    }

    public boolean deleteById(String id) {
        return stations.remove(id) != null;
    }
}
