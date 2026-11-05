package com.rackguard.rackguardserver.service;

import com.rackguard.rackguardserver.model.AccessRequest;
import com.rackguard.rackguardserver.model.DoorEvent;
import com.rackguard.rackguardserver.repository.DbObjectsRepository;
import com.rackguard.rackguardserver.repository.DbObjectsRepository.AnomalyRow;
import com.rackguard.rackguardserver.repository.EventRepository;
import com.rackguard.rackguardserver.repository.EventRepository.Visit;
import com.rackguard.rackguardserver.repository.RequestRepository;
import com.rackguard.rackguardserver.store.AppData.Prefs;
import com.rackguard.rackguardserver.util.Const;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {
    public enum Period { DAY, WEEK, MONTH }
    public record Aggregate(LocalDate periodStart, Integer roomId, Integer userId, int visits, long totalSeconds) {}
    public record Stats(int visitCount, double meanSeconds, double medianSeconds, double p95Seconds,
                        List<Integer> peakHours, double rejectedShare) {}
    public record TempPoint(Integer doorId, OffsetDateTime time, double value, String unit) {}
    public record Dashboard(Stats stats, int anomalyCount, List<AnomalyRow> recentAnomalies,
                            List<EventService.DeviceStatus> devices, List<TempPoint> temperature,
                            List<SystemService.CertView> certificateWarnings) {}

    private final EventRepository events;
    private final RequestRepository requests;
    private final DbObjectsRepository db;
    private final EventService eventService;
    private final SystemService system;
    private final PrefsService prefs;

    public AnalyticsService(EventRepository events, RequestRepository requests, DbObjectsRepository db,
                            EventService eventService, SystemService system, PrefsService prefs) {
        this.events = events;
        this.requests = requests;
        this.db = db;
        this.eventService = eventService;
        this.system = system;
        this.prefs = prefs;
    }

    private List<Visit> visits(LocalDateTime from, LocalDateTime to) {
        return events.findVisits(from, to).stream().filter(v -> v.lastOut() != null && !v.lastOut().isBefore(v.firstIn())).toList();
    }

    private static long seconds(Visit v) { return Duration.between(v.firstIn(), v.lastOut()).getSeconds(); }

    private static LocalDate periodStart(LocalDate d, Period p) {
        return switch (p) {
            case DAY -> d;
            case WEEK -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> d.withDayOfMonth(1);
        };
    }

    public List<Aggregate> aggregate(LocalDateTime from, LocalDateTime to, Period period) {
        return visits(from, to).stream().collect(Collectors.groupingBy(
                        v -> Arrays.asList(periodStart(v.firstIn().toLocalDate(), period), v.roomId(), v.userId())))
                .entrySet().stream()
                .map(e -> new Aggregate((LocalDate) e.getKey().get(0), (Integer) e.getKey().get(1),
                        (Integer) e.getKey().get(2), e.getValue().size(),
                        e.getValue().stream().mapToLong(AnalyticsService::seconds).sum()))
                .sorted(Comparator.comparing(Aggregate::periodStart)).toList();
    }

    public Stats stats(LocalDateTime from, LocalDateTime to) {
        List<Visit> visits = visits(from, to);
        double[] d = visits.stream().mapToLong(AnalyticsService::seconds).sorted().asDoubleStream().toArray();
        double mean = Arrays.stream(d).average().orElse(0);
        double median = d.length == 0 ? 0 : d.length % 2 == 1 ? d[d.length / 2] : (d[d.length / 2 - 1] + d[d.length / 2]) / 2;
        double p95 = d.length == 0 ? 0 : d[(int) Math.ceil(0.95 * d.length) - 1];                // nearest rank
        List<Integer> peaks = visits.stream().collect(Collectors.groupingBy(v -> v.firstIn().getHour(), Collectors.counting()))
                .entrySet().stream().sorted(Map.Entry.<Integer, Long>comparingByValue().reversed()).limit(3)
                .map(Map.Entry::getKey).toList();
        List<AccessRequest> all = requests.findBetween(from, to);
        double rejected = all.isEmpty() ? 0 : (double) all.stream().filter(r -> Const.REJECTED.equals(r.status())).count() / all.size();
        return new Stats(visits.size(), mean, median, p95, peaks, rejected);
    }

    public Dashboard dashboard(LocalDateTime from, LocalDateTime to, Integer adminId) {
        Prefs p = prefs.get(adminId);
        List<AnomalyRow> anomalies = db.anomalies(from, to);
        List<TempPoint> temps = new ArrayList<>();
        for (DoorEvent e : events.find(new EventRepository.Filter(from, to, null, null, Const.TEMPERATURE))) {
            double c = parseTemp(e.note());
            if (!Double.isNaN(c)) temps.add(new TempPoint(e.doorId(), prefs.toOffset(e.eventTime(), p),
                    prefs.temperature(c, p), prefs.temperatureUnit(p)));
        }
        return new Dashboard(stats(from, to), anomalies.size(), anomalies.stream().limit(10).toList(),
                eventService.devices(p), temps, system.certificateWarnings());
    }

    private static double parseTemp(String note) {
        int i = note == null ? -1 : note.lastIndexOf("temp=");
        try { return i < 0 ? Double.NaN : Double.parseDouble(note.substring(i + 5).trim()); }
        catch (NumberFormatException e) { return Double.NaN; }
    }
}