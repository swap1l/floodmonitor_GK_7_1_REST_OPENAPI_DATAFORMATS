/*
 * Webclient für die Flood-Monitor-API.
 * Alle Daten werden je nach gewähltem Format als JSON oder XML angefordert (Accept-Header)
 * und in dieselbe JavaScript-Struktur umgewandelt, damit die Darstellung für beide Formate gleich ist.
 */
$(function () {
    'use strict';

    const API = 'api/v1';
    const MIME = { json: 'application/json', xml: 'application/xml' };

    let selectedStationId = null;
    let refreshTimer = null;
    let pendingRequests = 0;

    // ---------- HTTP + Formate ----------

    function currentFormat() {
        return $('input[name=format]:checked').val();
    }

    /**
     * GET-Anfrage im gewählten Format. Liefert ein Promise mit den geparsten Daten
     * (bei XML in dieselbe Objektstruktur wie bei JSON umgewandelt).
     */
    function apiGet(path, params) {
        const format = currentFormat();
        const deferred = $.Deferred();
        setLoading(+1);
        $.ajax({
            url: API + path,
            data: cleanParams(params),
            headers: { Accept: MIME[format] },
            dataType: 'text',          // selbst parsen, damit JSON und XML gleich behandelt werden
            timeout: 10000
        }).done(function (text, status, xhr) {
            showRaw(path, xhr, text);
            try {
                deferred.resolve(parseBody(text, xhr.getResponseHeader('Content-Type')));
            } catch (e) {
                deferred.reject({ status: xhr.status, message: 'Antwort konnte nicht gelesen werden: ' + e.message });
            }
        }).fail(function (xhr, textStatus) {
            showRaw(path, xhr, xhr.responseText);
            deferred.reject(toError(xhr, textStatus));
        }).always(function () {
            setLoading(-1);
        });
        return deferred.promise();
    }

    function cleanParams(params) {
        const result = {};
        $.each(params || {}, function (key, value) {
            if (value !== undefined && value !== null && value !== '') {
                result[key] = value;
            }
        });
        return result;
    }

    function parseBody(text, contentType) {
        if (!text) {
            return null;
        }
        if (contentType && contentType.indexOf('xml') !== -1) {
            return xmlToObject($.parseXML(text).documentElement);
        }
        return JSON.parse(text);
    }

    /**
     * Wandelt ein XML-Element in ein JS-Objekt um.
     * Listen kommen von Jackson als <List><item>…</item></List>, alles andere als Element mit Kindelementen.
     */
    function xmlToObject(element) {
        const children = $(element).children();
        if (element.nodeName === 'List') {
            return children.map(function () { return xmlToObject(this); }).get();
        }
        if (children.length === 0) {
            return xmlValue($(element).text());
        }
        const obj = {};
        children.each(function () {
            obj[this.nodeName] = xmlToObject(this);
        });
        return obj;
    }

    /** XML kennt keine Datentypen – Zahlen und Wahrheitswerte wie im JSON zurückgewinnen. */
    function xmlValue(text) {
        if (text === '') return null;
        if (text === 'true') return true;
        if (text === 'false') return false;
        if (/^-?\d+(\.\d+)?(E-?\d+)?$/i.test(text)) return Number(text);
        return text;
    }

    /** Fehler-Objekt mit verständlicher Meldung aus der ApiError-Antwort (JSON oder XML). */
    function toError(xhr, textStatus) {
        if (xhr.status === 0) {
            return { status: 0, message: textStatus === 'timeout'
                    ? 'Zeitüberschreitung – der Server antwortet nicht.'
                    : 'Server nicht erreichbar. Läuft die Anwendung?' };
        }
        if (xhr.status === 406) {
            return { status: 406, message: 'Das gewählte Format wird vom Server nicht unterstützt (406).' };
        }
        let message = null;
        try {
            const body = parseBody(xhr.responseText, xhr.getResponseHeader('Content-Type'));
            message = body && body.message;
        } catch (ignored) { /* kein lesbarer Fehler-Body */ }
        return { status: xhr.status, message: (message || 'Unerwarteter Fehler') + ' (HTTP ' + xhr.status + ')' };
    }

    // ---------- Anzeige-Hilfen ----------

    function setLoading(delta) {
        pendingRequests += delta;
        $('#loading').prop('hidden', pendingRequests === 0);
    }

    function showError(message) {
        $('#error').text(message).prop('hidden', !message);
    }

    function showRaw(path, xhr, text) {
        $('#raw-info').text('GET ' + API + path + ' → HTTP ' + xhr.status + ' ' +
            (xhr.getResponseHeader('Content-Type') || ''));
        let pretty = text || '';
        if (pretty && currentFormat() === 'json') {
            try { pretty = JSON.stringify(JSON.parse(pretty), null, 2); } catch (ignored) { /* Rohtext */ }
        }
        $('#raw').text(pretty);
    }

    function esc(value) {
        return $('<div>').text(value === null || value === undefined ? '–' : value).html();
    }

    function num(value, digits) {
        return value === null || value === undefined ? '–' : Number(value).toFixed(digits === undefined ? 1 : digits);
    }

    function formatTime(iso) {
        return iso ? new Date(iso).toLocaleString('de-AT') : '–';
    }

    function badge(level) {
        const l = level || 'UNKNOWN';
        return '<span class="badge level-' + esc(l) + '">' + esc(l) + '</span>';
    }

    function metric(label, value) {
        return '<div class="metric"><div class="label">' + esc(label) + '</div><div class="value">' + value + '</div></div>';
    }

    /** datetime-local (lokale Zeit) → ISO-8601 in UTC, wie von der API erwartet. */
    function toIso(localValue) {
        return localValue ? new Date(localValue).toISOString() : '';
    }

    // ---------- Laden + Darstellen ----------

    function loadStationSelect() {
        return apiGet('/stations').done(function (stations) {
            const select = $('#station-select');
            const rivers = $('#station-river');
            const selectedRiver = rivers.val();
            select.empty();
            rivers.find('option:not(:first)').remove();
            $.each(stations, function (i, s) {
                select.append($('<option>').val(s.id).text(s.id + ' – ' + s.name));
            });
            $.each([...new Set(stations.map(s => s.river))].sort(), function (i, river) {
                rivers.append($('<option>').val(river).text(river));
            });
            rivers.val(selectedRiver);
            if (!selectedStationId && stations.length) {
                selectedStationId = (stations.find(s => s.isActive) || stations[0]).id;
            }
            select.val(selectedStationId);
        });
    }

    function loadStations() {
        return apiGet('/stations', {
            river: $('#station-river').val(),
            warningLevel: $('#station-warning').val()
        }).done(function (stations) {
            const list = $('#stations').empty();
            if (!stations.length) {
                list.append('<p class="muted">Keine Station passt zum Filter.</p>');
            }
            $.each(stations, function (i, s) {
                $('<div class="station">')
                    .toggleClass('selected', s.id === selectedStationId)
                    .toggleClass('inactive', !s.isActive)
                    .attr('data-id', s.id)
                    .html('<div><strong>' + esc(s.id) + '</strong> ' + esc(s.name) +
                        '<small>' + esc(s.river) + ' · ' + esc(s.status) + (s.isActive ? '' : ' · deaktiviert') +
                        ' · Warnung ab ' + num(s.warningWaterLevel, 0) + ' cm, kritisch ab ' +
                        num(s.criticalWaterLevel, 0) + ' cm</small></div>' + badge(s.currentWarningLevel))
                    .appendTo(list);
            });
        });
    }

    function loadAlerts() {
        return apiGet('/alerts').done(function (alerts) {
            const list = $('#alerts').empty();
            if (!alerts.length) {
                list.append('<li class="muted">Keine aktiven Warnungen</li>');
            }
            $.each(alerts, function (i, a) {
                list.append('<li>' + badge(a.warningLevel) + '<span>' + esc(a.stationName) + ' (' + esc(a.river) +
                    '): ' + num(a.waterLevel) + ' cm – ' + formatTime(a.timestamp) + '</span></li>');
            });
        });
    }

    function loadLatest() {
        $('#latest-station').text(selectedStationId);
        return apiGet('/stations/' + encodeURIComponent(selectedStationId) + '/measurements/latest')
            .done(function (m) {
                $('#latest').html(
                    metric('Warnstufe', badge(m.warningLevel)) +
                    metric('Zeitpunkt', '<span style="font-size:13px">' + esc(formatTime(m.timestamp)) + '</span>') +
                    metric('Wasserstand', num(m.waterLevel) + ' cm') +
                    metric('Durchfluss', num(m.flowRate) + ' m³/s') +
                    metric('Niederschlag', num(m.rainfall) + ' mm/h') +
                    metric('Temperatur', num(m.temperature) + ' °C') +
                    metric('Akku', esc(m.batteryLevel) + ' %') +
                    metric('Status', esc(m.status)));
            })
            .fail(function (err) {
                // z. B. deaktivierte Station ohne Messung → im Panel statt als globaler Fehler anzeigen
                $('#latest').html('<p class="muted">' + esc(err.message) + '</p>');
            });
    }

    function historyParams() {
        return {
            from: toIso($('#from').val()),
            to: toIso($('#to').val()),
            warningLevel: $('#warning-level').val(),
            limit: $('#limit').val()
        };
    }

    function loadHistory() {
        const params = historyParams();
        return apiGet('/stations/' + encodeURIComponent(selectedStationId) + '/measurements', params)
            .done(function (measurements) {
                const body = $('#history').empty();
                if (!measurements.length) {
                    body.append('<tr><td colspan="9" class="muted">Keine Messwerte für die gewählten Filter</td></tr>');
                }
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
            });
    }

    function loadStatistics() {
        const p = historyParams();
        return apiGet('/stations/' + encodeURIComponent(selectedStationId) + '/statistics', { from: p.from, to: p.to })
            .done(function (s) {
                $('#statistics').html(
                    metric('Messungen', esc(s.measurementCount)) +
                    metric('Min. Wasserstand', num(s.minWaterLevel) + ' cm') +
                    metric('Max. Wasserstand', num(s.maxWaterLevel) + ' cm') +
                    metric('Ø Wasserstand', num(s.avgWaterLevel) + ' cm') +
                    metric('Ø Durchfluss', num(s.avgFlowRate) + ' m³/s') +
                    metric('Σ Niederschlag', num(s.rainfallSum) + ' mm/h') +
                    metric('Warnungen', esc(s.warningCount)) +
                    metric('Kritisch', esc(s.criticalCount)));
            });
    }

    function validateFilter() {
        const from = $('#from').val();
        const to = $('#to').val();
        const limit = Number($('#limit').val());
        if (from && to && new Date(from) > new Date(to)) {
            return '„Von“ darf nicht nach „Bis“ liegen.';
        }
        if (!Number.isInteger(limit) || limit < 1 || limit > 1000) {
            return 'Die Anzahl muss zwischen 1 und 1000 liegen.';
        }
        return null;
    }

    /** Lädt alle Bereiche neu. Fehler werden gesammelt und verständlich angezeigt. */
    function refresh() {
        const errors = [];
        const collect = function (err) { errors.push(err.message); };
        const filterError = validateFilter();

        const requests = [loadStations().fail(collect), loadAlerts().fail(collect)];
        if (selectedStationId) {
            requests.push(loadLatest());
            if (filterError) {
                errors.push(filterError);
            } else {
                requests.push(loadHistory().fail(collect), loadStatistics().fail(collect));
            }
        }
        // $.when bricht beim ersten Fehler ab – daher jeden Request einzeln abwarten
        const settled = requests.map(r => r.then(() => null, () => $.Deferred().resolve()));
        $.when.apply($, settled).always(function () {
            showError([...new Set(errors)].join(' · '));
            $('#last-update').text('Stand: ' + new Date().toLocaleTimeString('de-AT') +
                ' (' + currentFormat().toUpperCase() + ')');
        });
    }

    function scheduleRefresh() {
        clearInterval(refreshTimer);
        if ($('#auto-refresh').is(':checked')) {
            refreshTimer = setInterval(refresh, Number($('#refresh-interval').val()));
        }
    }

    function selectStation(id) {
        selectedStationId = id;
        $('#station-select').val(id);
        $('.station').removeClass('selected').filter('[data-id="' + id + '"]').addClass('selected');
        refresh();
    }

    // ---------- Events ----------

    $('#stations').on('click', '.station', function () {
        selectStation($(this).attr('data-id'));
    });
    $('#station-select').on('change', function () { selectStation($(this).val()); });
    $('#station-river, #station-warning').on('change', refresh);
    $('#history-filter').on('submit', function (e) {
        e.preventDefault();
        refresh();
    });
    $('#reset-filter').on('click', function () {
        $('#from, #to').val('');
        $('#warning-level').val('');
        $('#limit').val(50);
        refresh();
    });
    $('input[name=format]').on('change', function () {
        loadStationSelect().always(refresh);
    });
    $('#refresh-now').on('click', refresh);
    $('#auto-refresh, #refresh-interval').on('change', scheduleRefresh);

    // ---------- Start ----------

    // Optional per URL vorbelegen, z. B. index.html?station=ST-002&format=xml
    const urlParams = new URLSearchParams(window.location.search);
    selectedStationId = urlParams.get('station');
    if (urlParams.get('format') === 'xml') {
        $('input[name=format][value=xml]').prop('checked', true);
    }

    loadStationSelect()
        .fail(function (err) { showError(err.message); })
        .always(function () {
            refresh();
            scheduleRefresh();
        });
});
