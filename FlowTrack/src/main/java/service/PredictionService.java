package service;

import model.Period;
import model.User;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class PredictionService {

    public static class PeriodRange {
        public LocalDate start;
        public LocalDate end;
        public int length;
        public PeriodRange(LocalDate start, LocalDate end) {
            this.start = start;
            this.end = end;
            this.length = (int) ChronoUnit.DAYS.between(start, end) + 1;
        }
    }

    private LocalDate addDays(LocalDate date, int days) { return date.plusDays(days); }
    private long daysBetween(LocalDate a, LocalDate b) { return ChronoUnit.DAYS.between(a, b); }

    /** Group consecutive days into ranges. */
    public List<PeriodRange> extractRanges(List<LocalDate> allDays) {
        List<PeriodRange> ranges = new ArrayList<>();
        if (allDays == null || allDays.isEmpty()) return ranges;

        List<LocalDate> sorted = new ArrayList<>(allDays);
        Collections.sort(sorted);

        LocalDate rangeStart = sorted.get(0);
        LocalDate prev = sorted.get(0);

        for (int i = 1; i < sorted.size(); i++) {
            LocalDate curr = sorted.get(i);
            if (curr.equals(prev.plusDays(1))) {
                prev = curr;
            } else {
                ranges.add(new PeriodRange(rangeStart, prev));
                rangeStart = curr;
                prev = curr;
            }
        }
        ranges.add(new PeriodRange(rangeStart, prev));
        return ranges;
    }

    public List<LocalDate> getAllDays(User user) {
        List<LocalDate> days = new ArrayList<>();
        if (user.getPeriods() != null) {
            for (Period p : user.getPeriods()) days.add(p.getStartDate());
        }
        return days;
    }

    public int calculatePredictedCycle(User user) {
        List<PeriodRange> ranges = extractRanges(getAllDays(user));
        List<LocalDate> starts = ranges.stream().map(r -> r.start).collect(Collectors.toList());

        if (starts.size() < 2) {
            return user.getCycleLength() != null ? user.getCycleLength() : 28;
        }

        List<Integer> lengths = new ArrayList<>();
        for (int i = 1; i < starts.size(); i++) {
            lengths.add((int) daysBetween(starts.get(i-1), starts.get(i)));
        }

        List<Integer> sorted = new ArrayList<>(lengths);
        Collections.sort(sorted);
        double median = sorted.get(sorted.size() / 2);
        double q1 = sorted.get((int)(sorted.size() * 0.25));
        double q3 = sorted.get((int)(sorted.size() * 0.75));
        double iqr = q3 - q1;
        double lower = Math.max(18, median - 1.5 * iqr);
        double upper = Math.min(45, median + 1.5 * iqr);

        List<Integer> filtered = lengths.stream()
                .filter(l -> l >= lower && l <= upper)
                .collect(Collectors.toList());
        if (filtered.isEmpty()) return (int) median;

        int weightSum = 0, weightedSum = 0;
        for (int i = 0; i < filtered.size(); i++) {
            int w = Math.min(i + 1, 4);
            weightedSum += filtered.get(i) * w;
            weightSum += w;
        }
        int weightedAvg = Math.round((float) weightedSum / weightSum);

        int userCycle = user.getCycleLength() != null ? user.getCycleLength() : 28;
        boolean varies = user.getCycleLengthVaries() != null && user.getCycleLengthVaries();
        double blend = varies ? 0.85 : 0.7;
        int blended = (int) Math.round(weightedAvg * blend + userCycle * (1 - blend));
        return Math.min(45, Math.max(20, blended));
    }


    public int averagePeriodLength(User user) {
        List<PeriodRange> ranges = extractRanges(getAllDays(user));
        if (!ranges.isEmpty()) {
            return Math.max(1, (int) Math.round(ranges.stream().mapToInt(r -> r.length).average().orElse(5)));
        }
        return user.getPeriodLength() != null && user.getPeriodLength() > 0 ? user.getPeriodLength() : 5;
    }

    private LocalDate getLastKnownStart(User user) {
        List<PeriodRange> ranges = extractRanges(getAllDays(user));
        if (!ranges.isEmpty()) return ranges.get(ranges.size() - 1).start;
        return user.getLastPeriodStart();
    }

    public LocalDate predictNextPeriod(User user) {
        LocalDate lastStart = getLastKnownStart(user);
        if (lastStart == null) return null;
        return addDays(lastStart, calculatePredictedCycle(user));
    }

    public LocalDate predictOvulation(User user) {
        LocalDate next = predictNextPeriod(user);
        return next == null ? null : addDays(next, -14);
    }
    public Map<String, LocalDate> calculateFertileWindow(User user) {
        LocalDate ov = predictOvulation(user);
        if (ov == null) return null;
        Map<String, LocalDate> w = new HashMap<>();
        w.put("start", addDays(ov, -5));
        w.put("end", ov);
        return w;
    }

    public Set<LocalDate> getAllPeriodDays(User user, int cyclesAhead) {
        Set<LocalDate> days = new HashSet<>();
        if (getLastKnownStart(user) == null) return days;

     
        for (LocalDate d : getAllDays(user)) days.add(d);

        int cycleLen = calculatePredictedCycle(user);
        int periodLen = averagePeriodLength(user);
        LocalDate next = predictNextPeriod(user);

        for (int i = 0; i < cyclesAhead && next != null; i++) {
            for (int j = 0; j < periodLen; j++) days.add(addDays(next, j));
            next = addDays(next, cycleLen);
        }
        return days;
    }

 
    public List<LocalDate> getPredictedStarts(User user, int cyclesAhead) {
        List<LocalDate> starts = new ArrayList<>();
        if (getLastKnownStart(user) == null) return starts;

        int cycleLen = calculatePredictedCycle(user);
        LocalDate next = predictNextPeriod(user);

        for (int i = 0; i < cyclesAhead && next != null; i++) {
            starts.add(next);
            next = addDays(next, cycleLen);
        }
        return starts;
    }

   
    public Map<String, String> getAllPhases(User user, int monthsAhead) {
        Map<String, String> phases = new HashMap<>();
        LocalDate lastStart = getLastKnownStart(user);
        if (lastStart == null) return phases;

        int cycleLen = calculatePredictedCycle(user);
        int periodLen = averagePeriodLength(user);

        
        int ovulationDay = cycleLen - 14;
        int fertileStartDay = ovulationDay - 5; 
        int fertileEndDay = ovulationDay;

        // Sanity — keep phases in order
        if (fertileStartDay < periodLen + 1) fertileStartDay = periodLen + 1;
        if (fertileEndDay < fertileStartDay) fertileEndDay = fertileStartDay;

        // Range to color: from the first historical period, forward N months from today
        List<PeriodRange> ranges = extractRanges(getAllDays(user));
        LocalDate rangeStart = ranges.isEmpty() ? lastStart : ranges.get(0).start;
        LocalDate rangeEnd = LocalDate.now().plusMonths(monthsAhead).plusDays(7);

        LocalDate d = rangeStart;
        while (!d.isAfter(rangeEnd)) {
           
            LocalDate cycleStart = lastStart;
            if (d.isBefore(lastStart)) {
             
                for (int i = ranges.size() - 1; i >= 0; i--) {
                    if (!ranges.get(i).start.isAfter(d)) {
                        cycleStart = ranges.get(i).start;
                        break;
                    }
                }
            } else {
               
                long diff = ChronoUnit.DAYS.between(lastStart, d);
                long fullCycles = diff / cycleLen;
                cycleStart = lastStart.plusDays(fullCycles * cycleLen);
            }

            long dayInCycle = ChronoUnit.DAYS.between(cycleStart, d) + 1;
            if (dayInCycle <= 0) { d = d.plusDays(1); continue; }

            String phase;
            if (dayInCycle <= periodLen) phase = "menstrual";
            else if (dayInCycle < fertileStartDay) phase = "follicular";
            else if (dayInCycle <= fertileEndDay) phase = "ovulation";
            else phase = "luteal";

            phases.put(d.toString(), phase);
            d = d.plusDays(1);
        }

        return phases;
    }

    public int calculateConfidence(User user) {
        List<PeriodRange> ranges = extractRanges(getAllDays(user));
        if (ranges.size() < 2) return 20;

        List<Integer> lengths = new ArrayList<>();
        for (int i = 1; i < ranges.size(); i++) {
            lengths.add((int) daysBetween(ranges.get(i-1).start, ranges.get(i).start));
        }

        double avg = lengths.stream().mapToInt(Integer::intValue).average().orElse(0);
        double variance = lengths.stream().mapToDouble(l -> Math.pow(l - avg, 2)).average().orElse(0);
        double stdDev = Math.sqrt(variance);

        int conf = Math.min(70, 20 + lengths.size() * 5);
        if (stdDev <= 1) conf += 20;
        else if (stdDev <= 2) conf += 10;
        else if (stdDev <= 3) conf += 5;
        else conf -= 10;
        if (user.getCycleLength() != null && Math.abs(user.getCycleLength() - avg) <= 2) conf += 5;
        if (Boolean.TRUE.equals(user.getCycleLengthVaries())) conf -= 10;

        return Math.min(95, Math.max(10, conf));
    }
}