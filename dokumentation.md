# Dokumentation – Flood Monitor (Hochwasser-Frühwarnsystem)

Diese Dokumentation geht die Aufgabenstellung **Schritt für Schritt** durch. Zu jedem Schritt steht der
wichtigste Code und darunter kurz, was er macht. Ausführliche Infos (Startanleitung, alle Beispiele,
Fragestellungen mit Quellen) stehen in der [README.md](README.md).

| Schritt | Thema | Wichtigste Dateien |
|---|---|---|
| 1 | Projekt einrichten | `build.gradle`, `application.properties` |
| – | Datenmodell | `model/*` |
| 2 | Messwerte simulieren | `MeasurementSimulator`, `SimulationScheduler`, `MeasurementService` |
| 3 | Warnstufe berechnen | `WarningLevelService` |
| 4 | REST-Schnittstelle | `StationController`, `AlertController`, `StationService`, `MeasurementService`, `AlertService` |
| 5 | JSON und XML | `WebConfig`, `XmlSuffixContentNegotiationStrategy`, `NonBrowserHeaderContentNegotiationStrategy` |
| 6 | Statuscodes und Fehler | `GlobalExceptionHandler`, `ApiKeyInterceptor`, Exceptions |
| 7 | Webclient | `static/index.html`, `static/js/app.js`, `static/css/style.css` |
| 8 | Architektur | Paketstruktur |
| 9 | Tests | `FloodMonitorApiTest`, `StationWriteApiTest`, `SimulationRulesTest` |
| 10 | OpenAPI | `OpenApiConfig`, Annotationen in den Controllern |

---

## 1. Projekt einrichten

### 1.1 Abhängigkeiten (`build.gradle`)

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.5.7'
    id 'io.spring.dependency-management' version '1.1.7'
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'com.fasterxml.jackson.dataformat:jackson-dataformat-xml'
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.5'
    implementation 'org.webjars:jquery:3.6.1'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

Das Projekt ist ein Spring-Boot-Projekt mit Gradle. `starter-web` liefert den Webserver und REST,
`jackson-dataformat-xml` macht XML möglich, `springdoc` erzeugt die API-Doku und `jquery` wird für den Webclient mitgeliefert.

### 1.2 Konfiguration (`application.properties`)

```properties
simulation.interval=10000
simulation.enabled=true
simulation.history-size=60
warning.stale-after-intervals=3
api.key=${FLOODMONITOR_API_KEY:dev-key-change-me}
server.error.include-stacktrace=never
```

Hier wird eingestellt, wie oft gemessen wird (alle 10 s) und ob die Simulation läuft.
Der API-Key kommt aus einer Umgebungsvariable, damit kein Passwort im Code steht.

### 1.3 Starten

```bash
gradlew.bat bootRun
```

Startet die Anwendung. Danach ist der Webclient unter http://localhost:8080/ erreichbar.

---

## Datenmodell

### Enums

```java
public enum WarningLevel {
    NORMAL, WARNING, CRITICAL, UNKNOWN
}

public enum StationStatus {
    ONLINE, MAINTENANCE, OFFLINE
}
```

Enums legen die erlaubten Werte fest. So kann z. B. keine Warnstufe „PANIC“ entstehen.

### Station und Location

```java
public class Station {
    private String id;
    private String name;
    private String river;
    private Location location;          // latitude + longitude
    private double normalWaterLevel;
    private double warningWaterLevel;
    private double criticalWaterLevel;
    @JsonProperty("isActive")
    private volatile boolean active;
    private volatile StationStatus status = StationStatus.ONLINE;
    private volatile WarningLevel currentWarningLevel = WarningLevel.UNKNOWN;
    // ...
}
```

Eine Station hat einen Namen, einen Fluss, eine Position und drei Grenzwerte für den Wasserstand.
`@JsonProperty("isActive")` sorgt dafür, dass das Feld in JSON/XML genau `isActive` heißt.

### Measurement

```java
@JacksonXmlRootElement(localName = "measurement")
@JsonPropertyOrder({"stationId", "timestamp", "waterLevel", "flowRate", "rainfall",
        "temperature", "batteryLevel", "status", "warningLevel"})
public class Measurement {
    private String stationId;
    private Instant timestamp;      // java.time → ISO-8601, z. B. 2026-05-01T10:15:00Z
    private double waterLevel;      // cm
    private double flowRate;        // m³/s
    private double rainfall;        // mm/h
    private double temperature;     // °C
    private int batteryLevel;       // %
    private StationStatus status;
    private WarningLevel warningLevel;
}
```

Eine Messung enthält alle geforderten Werte. Der Zeitpunkt ist ein `Instant` aus `java.time`.
Die Annotationen legen den XML-Namen `<measurement>` und die Reihenfolge der Felder fest.

### Die Stationen (`StationRepository`)

```java
save(new Station("ST-001", "Wien Reichsbrücke", "Donau", new Location(48.2289, 16.4106),
        280, 450, 600, true, 1900, 14));
save(new Station("ST-002", "Linz", "Donau", ...));
save(new Station("ST-003", "Innsbruck", "Inn", ...));
save(new Station("ST-004", "Salzburg", "Salzach", ...));
save(new Station("ST-005", "Graz", "Mur", ..., false, ...));   // deaktiviert
```

Beim Start werden fünf Stationen angelegt, gefordert sind mindestens drei.
Graz ist deaktiviert, damit man sieht, dass dort keine Messungen entstehen.

---

## 2. Messwerte simulieren

### 2.1 Regelmäßig auslösen (`SimulationScheduler`)

```java
@Component
@ConditionalOnProperty(name = "simulation.enabled", havingValue = "true", matchIfMissing = true)
public class SimulationScheduler {

    @EventListener(ApplicationReadyEvent.class)
    public void createHistory() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        for (int i = historySize; i > 0; i--) {
            measurementService.simulateTick(now.minus(interval.multipliedBy(i)));
        }
    }

    @Scheduled(fixedRateString = "${simulation.interval:10000}", initialDelayString = "${simulation.interval:10000}")
    public void tick() {
        measurementService.simulateTick(Instant.now().truncatedTo(ChronoUnit.SECONDS));
    }
}
```

Beim Start werden 60 Messungen „in der Vergangenheit“ erzeugt, damit sofort Verlaufsdaten da sind.
Danach erzeugt `@Scheduled` alle `simulation.interval` Millisekunden neue Messungen.
Mit `simulation.enabled=false` (in Tests) wird die Klasse gar nicht geladen.

### 2.2 Nur aktive Stationen messen (`MeasurementService`)

```java
public void simulateTick(Instant timestamp) {
    for (Station station : stationRepository.findAll()) {
        if (!station.isActive() || station.getStatus() == StationStatus.OFFLINE) {
            continue;
        }
        Measurement previous = measurementRepository.findLatest(station.getId()).orElse(null);
        Measurement next = simulator.next(station, previous, timestamp);
        addMeasurement(station, next, previous);
    }
}
```

Deaktivierte oder ausgefallene Stationen werden übersprungen.
Für alle anderen wird aus der **vorherigen Messung** die nächste berechnet.

### 2.3 Nächste Messung berechnen (`MeasurementSimulator.next`)

```java
double rainfall = nextRainfall(previous.getRainfall());
double waterLevel = nextWaterLevel(station, previous.getWaterLevel(), rainfall);
double flowRate = flowRate(station, waterLevel);
```

Die Werte hängen voneinander ab: zuerst der Regen, daraus der Wasserstand, daraus der Durchfluss.
Dadurch passen die Werte logisch zusammen.

#### a) Niederschlag

```java
double nextRainfall(double previousRainfall) {
    if (previousRainfall <= 0) {
        if (random.nextDouble() >= RAIN_START_PROBABILITY) {        // 4 %
            return 0.0;
        }
        return random.nextDouble() < HEAVY_RAIN_PROBABILITY          // 20 % Starkregen
                ? 10 + random.nextDouble() * 15
                : 0.5 + random.nextDouble() * 6;
    }
    if (random.nextDouble() < RAIN_STOP_PROBABILITY) {               // 5 %
        return 0.0;
    }
    double rainfall = previousRainfall * (RAIN_TREND_MIN + random.nextDouble() * RAIN_TREND_RANGE)
            + random.nextGaussian() * 0.5;
    return clamp(rainfall, 0.1, MAX_RAINFALL);
}
```

Wenn es trocken ist, beginnt nur selten ein Regen. Wenn es regnet, regnet es meistens weiter und die Stärke ändert sich nur ein wenig.
So entstehen realistische Regenphasen statt zufälliger Sprünge.

#### b) Wasserstand

```java
double nextWaterLevel(Station station, double previousLevel, double rainfall) {
    double normal = station.getNormalWaterLevel();
    double max = maxWaterLevel(station);
    double headroom = clamp((max - previousLevel) / (max - station.getWarningWaterLevel()), 0, 1);
    double delta = rainfall * RAIN_EFFECT_CM_PER_MM * headroom
            - RECESSION_RATE * (previousLevel - normal)
            + random.nextGaussian();
    delta = clamp(delta, -MAX_LEVEL_CHANGE_CM, MAX_LEVEL_CHANGE_CM);
    return Math.max(0, previousLevel + delta);
}
```

Regen hebt den Wasserstand, ohne Regen sinkt er langsam wieder Richtung Normalwert.
Pro Messung ändert er sich höchstens um 25 cm und wird nie negativ.
Oberhalb des Warnwerts wirkt Regen schwächer (`headroom`), damit der Pegel nicht unrealistisch hoch steigt.

#### c) Durchflussmenge

```java
double flowRate(Station station, double waterLevel) {
    double ratio = waterLevel / station.getNormalWaterLevel();
    double flow = station.getMeanFlowRate() * Math.pow(ratio, 1.5) * (1 + random.nextGaussian() * 0.01);
    return Math.max(0, flow);
}
```

Je höher der Wasserstand, desto mehr Wasser fließt durch.
Durch die Hochzahl 1,5 steigt der Durchfluss stärker als der Wasserstand selbst.

#### d) Akku und Status

```java
if (status == StationStatus.MAINTENANCE && random.nextDouble() < BATTERY_REPLACEMENT_PROBABILITY) {
    battery = 100; // Techniker hat den Akku getauscht
} else if (random.nextDouble() < BATTERY_DRAIN_PROBABILITY) {
    battery = Math.max(0, battery - 1);
}
if (battery == 0) {
    status = StationStatus.OFFLINE;
} else if (battery < MAINTENANCE_BATTERY_THRESHOLD) {   // 15 %
    status = StationStatus.MAINTENANCE;
} else {
    status = StationStatus.ONLINE;
}
```

Der Akku wird langsam leer und bleibt immer zwischen 0 und 100 %.
Unter 15 % geht die Station in `MAINTENANCE`, bei 0 % ist sie `OFFLINE` und misst nicht mehr.

---

## 3. Warnstufe berechnen (`WarningLevelService`)

### 3.1 Warnstufe einer Messung

```java
public WarningLevel calculate(Station station, Measurement measurement, Measurement previous) {
    if (measurement == null || !isValid(measurement.getWaterLevel())) {
        return WarningLevel.UNKNOWN;
    }
    double level = measurement.getWaterLevel();
    if (level >= station.getCriticalWaterLevel()) {
        return WarningLevel.CRITICAL;
    }
    if (level >= station.getWarningWaterLevel()) {
        return WarningLevel.WARNING;
    }
    if (previous != null && isValid(previous.getWaterLevel())
            && level >= station.getWarningWaterLevel() * EARLY_WARNING_RATIO       // 90 %
            && level - previous.getWaterLevel() >= EARLY_WARNING_RISE_CM          // +10 cm
            && measurement.getRainfall() >= EARLY_WARNING_RAINFALL) {            // 10 mm/h
        return WarningLevel.WARNING;
    }
    return WarningLevel.NORMAL;
}
```

Der Wasserstand wird mit den Grenzwerten der Station verglichen: ab kritischem Wert `CRITICAL`, ab Warnwert `WARNING`, sonst `NORMAL`.
Zusätzlich gibt es eine Frühwarnung, wenn der Pegel knapp unter dem Warnwert ist, schnell steigt und es stark regnet.

### 3.2 Nur der Server setzt die Warnstufe

```java
public Measurement addMeasurement(Station station, Measurement measurement, Measurement previous) {
    measurement.setWarningLevel(warningLevelService.calculate(station, measurement, previous));
    measurementRepository.save(measurement);
    station.setStatus(measurement.getStatus());
    return measurement;
}
```

Jede Messung bekommt ihre Warnstufe direkt vor dem Speichern vom Server.
Ein Client kann sie nicht vorgeben.

### 3.3 Veraltete Messung → UNKNOWN

```java
public WarningLevel current(Measurement latest, Instant now) {
    if (latest == null || latest.getWarningLevel() == null
            || latest.getTimestamp().isBefore(now.minus(maxAge))) {
        return WarningLevel.UNKNOWN;
    }
    return latest.getWarningLevel();
}
```

Gibt es keine Messung oder ist die letzte zu alt (älter als 3 Intervalle), ist die aktuelle Warnstufe `UNKNOWN`.

---

## 4. REST-Schnittstelle

### 4.1 Endpunkte (`StationController`, `AlertController`)

```java
@RestController
@RequestMapping(path = "/api/v1/stations",
        produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
public class StationController {

    @GetMapping({"", "/xml"})
    public List<Station> getStations(@RequestParam(required = false) String river,
                                     @RequestParam(required = false) Boolean isActive,
                                     @RequestParam(required = false) WarningLevel warningLevel) {
        return stationService.findAll(river, isActive, warningLevel);
    }

    @GetMapping({"/{stationId}", "/{stationId}/xml"})
    public Station getStation(@PathVariable String stationId) { ... }

    @GetMapping({"/{stationId}/measurements/latest", "/{stationId}/measurements/latest/xml"})
    public Measurement getLatestMeasurement(@PathVariable String stationId) { ... }

    @GetMapping({"/{stationId}/measurements", "/{stationId}/measurements/xml"})
    public List<Measurement> getMeasurements(@PathVariable String stationId,
                                             @RequestParam(required = false) Instant from,
                                             @RequestParam(required = false) Instant to,
                                             @RequestParam(required = false) Integer limit,
                                             @RequestParam(required = false) WarningLevel warningLevel) { ... }

    @GetMapping({"/{stationId}/statistics", "/{stationId}/statistics/xml"})
    public Statistics getStatistics(...) { ... }
}
```

```java
@RestController
@RequestMapping(path = "/api/v1/alerts", produces = {...})
public class AlertController {
    @GetMapping({"", "/xml"})
    public List<Alert> getAlerts(@RequestParam(required = false) WarningLevel warningLevel,
                                 @RequestParam(required = false) String river) { ... }
}
```

Jede Methode ist ein Endpunkt unter `/api/v1`. `@PathVariable` liest die Station-ID aus der URL, `@RequestParam` liest die optionalen Filter.
Die Controller geben die Arbeit sofort an einen Service weiter und enthalten selbst keine Logik.

| Methode | Endpunkt | Filter |
|---|---|---|
| GET | `/api/v1/stations` | `river`, `isActive`, `warningLevel` |
| GET | `/api/v1/stations/{id}` | |
| GET | `/api/v1/stations/{id}/measurements/latest` | |
| GET | `/api/v1/stations/{id}/measurements` | `from`, `to`, `limit`, `warningLevel` |
| GET | `/api/v1/stations/{id}/statistics` | `from`, `to` |
| GET | `/api/v1/alerts` | `warningLevel`, `river` |
| POST | `/api/v1/stations` | Body (JSON/XML), braucht API-Key |
| DELETE | `/api/v1/stations/{id}` | braucht API-Key |

### 4.2 Stationen filtern (`StationService`)

```java
public List<Station> findAll(String river, Boolean isActive, WarningLevel warningLevel) {
    return stationRepository.findAll().stream()
            .map(this::withCurrentWarningLevel)
            .filter(s -> river == null || s.getRiver().equalsIgnoreCase(river.trim()))
            .filter(s -> isActive == null || s.isActive() == isActive)
            .filter(s -> warningLevel == null || s.getCurrentWarningLevel() == warningLevel)
            .toList();
}
```

Zuerst bekommt jede Station ihre aktuelle Warnstufe, dann werden die Filter angewendet.
Ist ein Filter nicht angegeben (`null`), wird er ignoriert.

### 4.3 Historische Messwerte (`MeasurementService.getHistory`)

```java
int effectiveLimit = limit == null ? DEFAULT_LIMIT : limit;
if (effectiveLimit < 1 || effectiveLimit > MAX_LIMIT) {
    throw new InvalidRequestException("limit muss zwischen 1 und " + MAX_LIMIT + " liegen, war " + limit);
}
return inRange(stationId, from, to)
        .filter(m -> warningLevel == null || m.getWarningLevel() == warningLevel)
        .sorted(Comparator.comparing(Measurement::getTimestamp).reversed())
        .limit(effectiveLimit)
        .toList();
```

```java
private Stream<Measurement> inRange(String stationId, Instant from, Instant to) {
    stationService.findById(stationId);                       // 404, wenn unbekannt
    if (from != null && to != null && from.isAfter(to)) {
        throw new InvalidRequestException("from (" + from + ") darf nicht nach to (" + to + ") liegen");
    }
    return measurementRepository.findByStationId(stationId).stream()
            .filter(m -> from == null || !m.getTimestamp().isBefore(from))
            .filter(m -> to == null || !m.getTimestamp().isAfter(to));
}
```

`limit` muss zwischen 1 und 1000 liegen (Standard 100). Liegt `from` nach `to`, gibt es einen Fehler.
Danach werden die Messungen nach Zeitraum und Warnstufe gefiltert, die neuesten zuerst sortiert und gekürzt.

### 4.4 Aktuelle Warnungen (`AlertService`)

```java
return stationService.findAll(river, true, warningLevel).stream()
        .filter(s -> s.getCurrentWarningLevel() == WarningLevel.WARNING
                || s.getCurrentWarningLevel() == WarningLevel.CRITICAL)
        .map(this::toAlert)
        .filter(Objects::nonNull)
        .sorted(Comparator.comparing(Alert::getWarningLevel).reversed()
                .thenComparing(Alert::getStationId))
        .toList();
```

Eine Warnung gibt es für jede aktive Station mit `WARNING` oder `CRITICAL`.
Kritische Warnungen stehen ganz oben.

### 4.5 Statistik (`MeasurementService.getStatistics`)

```java
stats.setMeasurementCount(measurements.size());
stats.setMinWaterLevel(measurements.stream().mapToDouble(Measurement::getWaterLevel).min().getAsDouble());
stats.setMaxWaterLevel(measurements.stream().mapToDouble(Measurement::getWaterLevel).max().getAsDouble());
stats.setAvgWaterLevel(round(measurements.stream().mapToDouble(Measurement::getWaterLevel).average().getAsDouble()));
stats.setAvgFlowRate(round(measurements.stream().mapToDouble(Measurement::getFlowRate).average().getAsDouble()));
stats.setRainfallSum(round(measurements.stream().mapToDouble(Measurement::getRainfall).sum()));
stats.setWarningCount((int) measurements.stream()
        .filter(m -> m.getWarningLevel() == WarningLevel.WARNING || m.getWarningLevel() == WarningLevel.CRITICAL)
        .count());
stats.setCriticalCount((int) measurements.stream()
        .filter(m -> m.getWarningLevel() == WarningLevel.CRITICAL).count());
```

Mit Java-Streams werden Minimum, Maximum, Durchschnitte, die Summe des Niederschlags und die Zahl der Warnungen berechnet.
Man kann die Statistik auch auf einen Zeitraum (`from`, `to`) einschränken.

---

## 5. JSON und XML (Content Negotiation)

### 5.1 Format-Auswahl (`WebConfig`)

```java
@Override
public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
    configurer.strategies(List.of(
            new XmlSuffixContentNegotiationStrategy(),             // 1. URL endet auf /xml → XML
            new NonBrowserHeaderContentNegotiationStrategy(),      // 2. Accept-Header
            new FixedContentNegotiationStrategy(List.of(           // 3. sonst JSON
                    MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML))));
}
```

Spring prüft die Strategien der Reihe nach: zuerst die `/xml`-Endung, dann den `Accept`-Header und zuletzt JSON als Standard.
Passt das gewünschte Format zu keinem der beiden, antwortet Spring mit `406 Not Acceptable`.

### 5.2 Accept-Header, aber nicht vom Browser

```java
public List<MediaType> resolveMediaTypes(NativeWebRequest request) throws HttpMediaTypeNotAcceptableException {
    String[] accept = request.getHeaderValues(HttpHeaders.ACCEPT);
    if (accept != null && String.join(",", accept).contains(MediaType.TEXT_HTML_VALUE)) {
        return MEDIA_TYPE_ALL_LIST;
    }
    return super.resolveMediaTypes(request);
}
```

Browser schicken von sich aus auch `application/xml` im Accept-Header mit und würden dann XML bekommen.
Darum wird der Header ignoriert, wenn er `text/html` enthält, und der Browser bekommt JSON.

### 5.3 `/xml` am Ende der URL

```java
public List<MediaType> resolveMediaTypes(NativeWebRequest webRequest) {
    HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
    if (request != null && request.getRequestURI().endsWith(SUFFIX)) {
        return List.of(MediaType.APPLICATION_XML);
    }
    return MEDIA_TYPE_ALL_LIST;
}
```

Endet die URL auf `/xml` (z. B. `/api/v1/stations/xml`), wird immer XML geliefert.
Das ist praktisch, um XML direkt im Browser anzuschauen.

### 5.4 XML mit Deklaration

```java
@Bean
public MappingJackson2XmlHttpMessageConverter xmlHttpMessageConverter(Jackson2ObjectMapperBuilder builder) {
    XmlMapper xmlMapper = builder.createXmlMapper(true).build();
    xmlMapper.enable(ToXmlGenerator.Feature.WRITE_XML_DECLARATION);
    return new MappingJackson2XmlHttpMessageConverter(xmlMapper);
}
```

Der XML-Konverter schreibt die Zeile `<?xml version='1.0' encoding='UTF-8'?>` an den Anfang jeder Antwort.
JSON und XML werden aus denselben Java-Objekten erzeugt und enthalten deshalb dieselben Informationen.

**Beispiel:**

```bash
curl http://localhost:8080/api/v1/stations/ST-001/measurements/latest                                   # JSON
curl -H "Accept: application/xml" http://localhost:8080/api/v1/stations/ST-001/measurements/latest      # XML
curl -H "Accept: text/plain" -i http://localhost:8080/api/v1/stations                                   # 406
```

---

## 6. HTTP-Statuscodes und Fehlerbehandlung

### 6.1 Eigene Exceptions

```java
public class StationNotFoundException extends RuntimeException {
    public StationNotFoundException(String stationId) {
        super("Station " + stationId + " wurde nicht gefunden");
    }
}
// außerdem: InvalidRequestException, NoMeasurementException, StationConflictException,
//           UnauthorizedException, ForbiddenException
```

Für jeden Fehlerfall gibt es eine eigene Exception mit einer verständlichen Meldung.
Die Services werfen sie, wenn etwas nicht passt.

### 6.2 Zentrale Fehlerbehandlung (`GlobalExceptionHandler`)

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({StationNotFoundException.class, NoMeasurementException.class})
    public ResponseEntity<ApiError> handleNotFound(RuntimeException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);                  // 404
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiError> handleInvalid(...) { return build(HttpStatus.BAD_REQUEST, ...); }   // 400

    @ExceptionHandler(StationConflictException.class)
    public ResponseEntity<ApiError> handleConflict(...) { return build(HttpStatus.CONFLICT, ...); }     // 409

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Void> handleNotAcceptable() {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();                                // 406
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex, HttpServletRequest request) {
        // Spring-Fehler (404 Pfad, 405, 415 ...) behalten ihren Status
        log.error("Unerwarteter Fehler bei {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Interner Serverfehler", request);               // 500
    }
}
```

`@RestControllerAdvice` fängt alle Exceptions aus den Controllern an einer Stelle ab.
Jede Exception wird in den passenden Statuscode übersetzt. Der Stacktrace landet nur im Server-Log, nie beim Client.

### 6.3 Ungültige Parameter (Datum, Warnstufe)

```java
@ExceptionHandler(MethodArgumentTypeMismatchException.class)
public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
    String message = "Ungültiger Wert '" + ex.getValue() + "' für Parameter '" + ex.getName() + "'";
    if (type != null && type.isEnum()) {
        message += ", erlaubt: " + Arrays.toString(type.getEnumConstants());
    } else if (type == Instant.class) {
        message += ", erwartet ISO-8601, z. B. 2026-05-01T08:00:00Z";
    }
    return build(HttpStatus.BAD_REQUEST, message, request);
}
```

Kann Spring einen Parameter nicht umwandeln (z. B. `from=gestern` oder `warningLevel=PANIC`), kommt `400`.
Die Meldung sagt, welche Werte erlaubt sind.

### 6.4 Einheitliches Fehlerformat (`ApiError`)

```java
@JacksonXmlRootElement(localName = "error")
@JsonPropertyOrder({"timestamp", "status", "error", "message", "path"})
public class ApiError { ... }
```

```json
{
  "timestamp": "2026-05-01T10:20:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Station ST-999 wurde nicht gefunden",
  "path": "/api/v1/stations/ST-999"
}
```

Alle Fehler haben denselben Aufbau. Je nach Accept-Header kommen sie als JSON oder als XML.

### 6.5 Anmeldung: 401 und 403 (`ApiKeyInterceptor`)

```java
public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (READ_METHODS.contains(request.getMethod())) {           // GET, HEAD, OPTIONS
        return true;
    }
    String provided = request.getHeader(HEADER);                // X-API-Key
    if (provided == null || provided.isBlank()) {
        throw new UnauthorizedException("Für schreibende Zugriffe ist der Header " + HEADER + " erforderlich");
    }
    if (!MessageDigest.isEqual(apiKey, provided.getBytes(StandardCharsets.UTF_8))) {
        throw new ForbiddenException("Ungültiger API-Key");
    }
    return true;
}
```

Lesen ist für alle erlaubt. Für Anlegen oder Löschen braucht man den Header `X-API-Key`.
Fehlt er, kommt `401`, ist er falsch, kommt `403`.

### 6.6 Anlegen und Löschen: 201, 204, 409, 415

```java
@PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
public ResponseEntity<Station> createStation(@RequestBody StationRequest request) {
    Station station = stationService.create(request);
    return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                    .path("/{id}").buildAndExpand(station.getId()).toUri())
            .body(station);                                                      // 201 + Location
}

@DeleteMapping("/{stationId}")
public ResponseEntity<Void> deleteStation(@PathVariable String stationId) {
    stationService.delete(stationId);
    return ResponseEntity.noContent().build();                                   // 204
}
```

```java
if (!stationRepository.saveIfAbsent(station)) {
    throw new StationConflictException(station.getId());                         // 409
}
```

`POST` legt eine Station an und antwortet mit `201` und der Adresse der neuen Station. `DELETE` antwortet mit `204` ohne Inhalt.
Gibt es die ID schon, kommt `409`. Wird der Body nicht als JSON oder XML geschickt, kommt `415` (über `consumes`).

| Situation | Status |
|---|---:|
| erfolgreich | 200 |
| Station angelegt | 201 |
| Station gelöscht | 204 |
| ungültige Eingabe | 400 |
| API-Key fehlt | 401 |
| API-Key falsch | 403 |
| Station nicht gefunden | 404 |
| Format nicht unterstützt | 406 |
| Station existiert schon | 409 |
| Medientyp nicht unterstützt | 415 |
| interner Fehler | 500 |

---

## 7. Webclient (HTML, CSS, jQuery)

### 7.1 Daten im gewählten Format laden (`app.js`)

```javascript
const MIME = { json: 'application/json', xml: 'application/xml' };

function apiGet(path, params) {
    const format = currentFormat();                 // Radiobutton JSON / XML
    const deferred = $.Deferred();
    setLoading(+1);
    $.ajax({
        url: API + path,
        data: cleanParams(params),
        headers: { Accept: MIME[format] },
        dataType: 'text',
        timeout: 10000
    }).done(function (text, status, xhr) {
        deferred.resolve(parseBody(text, xhr.getResponseHeader('Content-Type')));
    }).fail(function (xhr, textStatus) {
        deferred.reject(toError(xhr, textStatus));
    }).always(function () {
        setLoading(-1);
    });
    return deferred.promise();
}
```

Alle Anfragen laufen über diese Funktion. Je nach gewähltem Format wird der `Accept`-Header auf JSON oder XML gesetzt.
Solange eine Anfrage läuft, wird „Lädt…“ angezeigt.

### 7.2 XML in dieselben Objekte wie JSON umwandeln

```javascript
function parseBody(text, contentType) {
    if (contentType && contentType.indexOf('xml') !== -1) {
        return xmlToObject($.parseXML(text).documentElement);
    }
    return JSON.parse(text);
}

function xmlToObject(element) {
    const children = $(element).children();
    if (element.nodeName === 'List') {                          // <List><item>…</item></List>
        return children.map(function () { return xmlToObject(this); }).get();
    }
    if (children.length === 0) {
        return xmlValue($(element).text());
    }
    const obj = {};
    children.each(function () { obj[this.nodeName] = xmlToObject(this); });
    return obj;
}
```

Eine XML-Antwort wird mit `$.parseXML` gelesen und in ein normales JavaScript-Objekt umgebaut.
Danach sieht sie genauso aus wie eine JSON-Antwort, deshalb ist die Anzeige für beide Formate gleich.

```javascript
function xmlValue(text) {
    if (text === '') return null;
    if (text === 'true') return true;
    if (text === 'false') return false;
    if (/^-?\d+(\.\d+)?(E-?\d+)?$/i.test(text)) return Number(text);
    return text;
}
```

In XML ist alles Text. Diese Funktion macht daraus wieder Zahlen und Wahrheitswerte.

### 7.3 Verständliche Fehlermeldungen

```javascript
function toError(xhr, textStatus) {
    if (xhr.status === 0) {
        return { status: 0, message: textStatus === 'timeout'
                ? 'Zeitüberschreitung – der Server antwortet nicht.'
                : 'Server nicht erreichbar. Läuft die Anwendung?' };
    }
    if (xhr.status === 406) {
        return { status: 406, message: 'Das gewählte Format wird vom Server nicht unterstützt (406).' };
    }
    const body = parseBody(xhr.responseText, xhr.getResponseHeader('Content-Type'));
    return { status: xhr.status, message: (body.message || 'Unerwarteter Fehler') + ' (HTTP ' + xhr.status + ')' };
}
```

Statt technischer Fehler zeigt der Client einen verständlichen Text an.
Bei API-Fehlern wird die `message` aus der `ApiError`-Antwort verwendet.

### 7.4 Tabelle mit Farben

```javascript
$.each(measurements, function (i, m) {
    const level = m.warningLevel || 'UNKNOWN';
    body.append('<tr class="row-' + esc(level) + '">' +
        '<td>' + esc(m.stationId) + '</td>' +
        '<td>' + esc(formatTime(m.timestamp)) + '</td>' +
        '<td class="num">' + num(m.waterLevel) + '</td>' +
        '<td class="num">' + num(m.flowRate) + '</td>' +
        '<td class="num">' + num(m.rainfall) + '</td>' +
        '<td class="num">' + num(m.temperature) + '</td>' +
        '<td class="num">' + esc(m.batteryLevel) + '</td>' +
        '<td>' + esc(m.status) + '</td>' +
        '<td>' + badge(level) + '</td></tr>');
});
```

```css
.level-NORMAL   { background: var(--normal); }    /* grün   */
.level-WARNING  { background: var(--warning); }   /* orange */
.level-CRITICAL { background: var(--critical); }  /* rot    */
.level-UNKNOWN  { background: var(--unknown); }   /* grau   */
```

Jede Messung wird eine Tabellenzeile mit allen geforderten Spalten.
Die Warnstufe bekommt eine CSS-Klasse und dadurch ihre Farbe. `esc()` schützt davor, dass Text als HTML ausgeführt wird.

### 7.5 Filter nach Zeitraum und Warnstufe

```javascript
function historyParams() {
    return {
        from: toIso($('#from').val()),             // datetime-local → ISO-8601 (UTC)
        to: toIso($('#to').val()),
        warningLevel: $('#warning-level').val(),
        limit: $('#limit').val()
    };
}

function validateFilter() {
    if (from && to && new Date(from) > new Date(to)) {
        return '„Von“ darf nicht nach „Bis“ liegen.';
    }
    if (!Number.isInteger(limit) || limit < 1 || limit > 1000) {
        return 'Die Anzahl muss zwischen 1 und 1000 liegen.';
    }
    return null;
}
```

Die Werte aus dem Formular werden als Query-Parameter an die API geschickt.
Offensichtliche Fehler prüft schon der Client, bevor er die Anfrage sendet.

### 7.6 Automatisch aktualisieren

```javascript
function scheduleRefresh() {
    clearInterval(refreshTimer);
    if ($('#auto-refresh').is(':checked')) {
        refreshTimer = setInterval(refresh, Number($('#refresh-interval').val()));
    }
}
```

Mit `setInterval` werden alle Daten regelmäßig neu geladen (5, 10, 30 oder 60 Sekunden).
Über die Checkbox lässt sich das abschalten.

---

## 8. Architektur

```
com.example.floodmonitor
├── model/        Datenklassen und Enums (Station, Measurement, WarningLevel, ...)
├── repository/   StationRepository, MeasurementRepository (Daten im Speicher)
├── service/      Geschäftslogik, Simulation, Warnstufenberechnung
├── controller/   StationController, AlertController (nur HTTP)
├── exception/    GlobalExceptionHandler + eigene Exceptions
└── config/       Content Negotiation, API-Key, OpenAPI
src/main/resources/static/   Webclient (HTML, CSS, JavaScript)
```

```text
Browser ──HTTP──▶ Controller ──▶ Service ──▶ Repository
                                   ▲
           SimulationScheduler ────┘  (MeasurementSimulator + WarningLevelService)
```

Jede Schicht hat genau eine Aufgabe: Der Controller nimmt die Anfrage an, der Service enthält die Logik und das Repository speichert die Daten.
Fehler gehen als Exception an den `GlobalExceptionHandler`.

Beispiel für einen schlanken Controller:

```java
@GetMapping({"/{stationId}", "/{stationId}/xml"})
public Station getStation(@PathVariable String stationId) {
    return stationService.findById(stationId);
}
```

Der Controller ruft nur den Service auf. Die Suche und der 404-Fehler stecken im Service.

---

## 9. Automatisierte Tests

### 9.1 Simulation in Tests abschalten

```properties
# src/test/resources/application.properties
simulation.enabled=false
api.key=test-key
```

In Tests läuft keine Simulation. So gibt es keine zufälligen Werte, und die Tests schlagen nie zufällig fehl.

### 9.2 Feste Testdaten

```java
@BeforeEach
void setUp() {
    measurementRepository.deleteAll();
    Station st1 = stationRepository.findById("ST-001").orElseThrow();   // Warnung 450, kritisch 600
    add(st1, NOW.minus(3, ChronoUnit.HOURS), 300);   // NORMAL
    add(st1, NOW.minus(2, ChronoUnit.HOURS), 460);   // WARNING
    add(st1, NOW.minus(1, ChronoUnit.HOURS), 610);   // CRITICAL
    add(st1, NOW, 320);                              // NORMAL
}
```

Vor jedem Test werden dieselben Messungen angelegt.
Damit sind alle drei Warnstufen enthalten und die Ergebnisse immer gleich.

### 9.3 Beispiele

```java
@Test
void stationsAsXml() throws Exception {
    mvc.perform(get("/api/v1/stations").accept(MediaType.APPLICATION_XML))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
            .andExpect(xpath("count(/List/item)").number(5.0));
}

@Test
void warningLevelsAreCalculatedServerSide() throws Exception {
    mvc.perform(get("/api/v1/stations/ST-001/measurements"))
            .andExpect(jsonPath("$[0].warningLevel").value("NORMAL"))
            .andExpect(jsonPath("$[1].warningLevel").value("CRITICAL"))
            .andExpect(jsonPath("$[2].warningLevel").value("WARNING"));
}

@Test
void limitBounds() throws Exception {
    mvc.perform(get("/api/v1/stations/ST-001/measurements").param("limit", "1000")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/stations/ST-001/measurements").param("limit", "1001")).andExpect(status().isBadRequest());
}
```

`MockMvc` schickt echte HTTP-Anfragen an die Anwendung, ohne einen Server zu starten.
Geprüft werden der Statuscode, das Format und der Inhalt (`jsonPath` für JSON, `xpath` für XML).

| Gefordert | Test |
|---|---|
| Stationen als JSON / XML | `stationsAsJsonByDefault`, `stationsAsXml` |
| existierende / unbekannte Station | `existingStation`, `unknownStation` |
| aktuellster Messwert | `latestMeasurement` |
| Filter nach Zeitraum / ungültiger Zeitraum | `filterByTimeRange`, `fromAfterToIsRejected` |
| NORMAL / WARNING / CRITICAL | `warningLevelsAreCalculatedServerSide`, `warningLevelThresholds` |
| nicht unterstütztes Format | `unsupportedFormat` |
| maximales Limit | `limitBounds` |
| 201 / 204 / 401 / 403 / 409 / 415 | `StationWriteApiTest` |
| Simulationsregeln | `SimulationRulesTest` |

Starten mit:

```bash
gradlew.bat test
```

---

## 10. API-Dokumentation (OpenAPI)

### 10.1 Allgemeine Infos und Authentifizierung (`OpenApiConfig`)

```java
@Bean
public OpenAPI floodMonitorOpenApi() {
    return new OpenAPI()
            .info(new Info().title("Flood Monitor API").version("v1").description("..."))
            .components(new Components().addSecuritySchemes(API_KEY_SCHEME, new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER)
                    .name(ApiKeyInterceptor.HEADER)));
}
```

Hier stehen Titel, Version und Beschreibung der API.
Außerdem wird festgelegt, dass man sich mit dem Header `X-API-Key` anmeldet.

### 10.2 Endpunkte beschreiben

```java
@Operation(summary = "Historische Messwerte abrufen", description = "Neueste zuerst. from/to sind inklusive.")
@ApiResponse(responseCode = "200", description = "Liste der Messungen")
@ApiResponse(responseCode = "404", description = "Station nicht gefunden",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
@GetMapping({"/{stationId}/measurements", "/{stationId}/measurements/xml"})
public List<Measurement> getMeasurements(
        @Parameter(description = "Beginn (ISO-8601)", example = "2026-05-01T08:00:00Z")
        @RequestParam(required = false) Instant from, ...
```

Mit Annotationen bekommt jeder Endpunkt eine Beschreibung, Parameter mit Beispielen und alle möglichen Statuscodes.
springdoc erzeugt daraus automatisch die OpenAPI-Datei und die Swagger-Oberfläche.

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

---

## 11. Technische Mindestanforderungen

| Anforderung | Umsetzung |
|---|---|
| Java und Spring Boot | Java 17+, Spring Boot 3.5.7 |
| Maven oder Gradle | Gradle (Wrapper `gradlew` ist dabei) |
| REST, JSON und XML | Spring Web + Jackson (JSON) + Jackson XML |
| HTML, CSS, JavaScript, jQuery | Webclient in `src/main/resources/static` |
| Git | Repository mit Commits |
| automatisierte Tests | 37 Tests mit JUnit 5 und MockMvc |

---

## 12. Fragestellungen

Die Antworten auf alle acht Fragen (JSON, XML, Content Negotiation, REST, HTTP-Methoden, neuer Endpunkt,
OpenAPI vs. Swagger, Fehler im Code-Snippet) stehen **mit Quellen** in der
[README.md, Abschnitt „Fragestellungen“](README.md#fragestellungen).

Kurzfassung zum Code-Snippet:

```java
@GetMapping("/stations/add")        // ❌ GET darf nichts verändern
public String createStation(@RequestParam String name, @RequestParam double warning) { ... }
```

```java
@PostMapping                        // ✅ POST /api/v1/stations, Body als JSON/XML, 201 Created
public ResponseEntity<Station> createStation(@RequestBody StationRequest request) { ... }
```

Zum Anlegen muss `POST` verwendet werden, weil `GET` keine Daten verändern darf. Das Verb „add“ gehört nicht in die URL.
Außerdem sollte die Antwort `201 Created` mit der neuen Station sein, nicht ein Text mit `200`.
