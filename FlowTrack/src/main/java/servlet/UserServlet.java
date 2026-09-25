package servlet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import util.DBUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/*")
public class UserServlet extends HttpServlet {

    private final Gson gson = new GsonBuilder().create();

    private Long userIdFromPath(String pathInfo) {
        if (pathInfo == null) return null;
        String[] parts = pathInfo.split("/");
        if (parts.length > 1) {
            try { return Long.parseLong(parts[1]); } catch (NumberFormatException e) {}
        }
        return null;
    }

    private String subPath(String pathInfo) {
        if (pathInfo == null) return "";
        String[] parts = pathInfo.split("/");
        return parts.length > 2 ? parts[2] : "";
    }

    /** Group a list of days into consecutive ranges (start, end, duration). */
    private List<Map<String, Object>> computeRanges(List<LocalDate> all) {
        List<Map<String, Object>> ranges = new ArrayList<>();
        if (all.isEmpty()) return ranges;

        LocalDate start = all.get(0);
        LocalDate prev = all.get(0);
        for (int i = 1; i < all.size(); i++) {
            LocalDate cur = all.get(i);
            if (cur.equals(prev.plusDays(1))) {
                prev = cur;
            } else {
                Map<String, Object> r = new HashMap<>();
                r.put("start", start.toString());
                r.put("end", prev.toString());
                r.put("duration", (int) ChronoUnit.DAYS.between(start, prev) + 1);
                ranges.add(r);
                start = cur;
                prev = cur;
            }
        }
        Map<String, Object> r = new HashMap<>();
        r.put("start", start.toString());
        r.put("end", prev.toString());
        r.put("duration", (int) ChronoUnit.DAYS.between(start, prev) + 1);
        ranges.add(r);
        return ranges;
    }

    /** Weighted average of cycle lengths (recent = higher weight). */
    private double computeWeightedCycleAvg(List<Integer> cycleLengths, int fallback) {
        if (cycleLengths == null || cycleLengths.isEmpty()) return fallback;
        double weightSum = 0, weightedSum = 0;
        for (int i = 0; i < cycleLengths.size(); i++) {
            int weight = Math.min(i + 1, 3);
            weightedSum += cycleLengths.get(i) * weight;
            weightSum += weight;
        }
        return weightedSum / weightSum;
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    // ══════════════════════════════════════════════════════
    // POST
    // ══════════════════════════════════════════════════════
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setContentType("application/json");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "";

        try (Connection conn = DBUtil.getConnection()) {

            // ── SIGNUP ──
            if (pathInfo.equals("/signup")) {
                Map<String, String> creds = gson.fromJson(req.getReader(), HashMap.class);
                String username = creds.get("username");
                String password = creds.get("password");

                PreparedStatement check = conn.prepareStatement(
                    "SELECT id FROM users WHERE username = ?");
                check.setString(1, username);
                ResultSet rsCheck = check.executeQuery();
                if (rsCheck.next()) {
                    resp.setStatus(409);
                    resp.getWriter().write("{\"error\":\"Username already exists\"}");
                    return;
                }

                PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO users (username, password) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
                ins.setString(1, username);
                ins.setString(2, password);
                ins.executeUpdate();

                ResultSet keys = ins.getGeneratedKeys();
                long id = keys.next() ? keys.getLong(1) : 0;

                Map<String, Object> out = new HashMap<>();
                out.put("id", id);
                out.put("username", username);
                resp.getWriter().write(gson.toJson(out));
                return;
            }

            // ── LOGIN ──
            if (pathInfo.equals("/login")) {
                Map<String, String> creds = gson.fromJson(req.getReader(), HashMap.class);
                String username = creds.get("username");
                String password = creds.get("password");

                PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM users WHERE username = ? AND password = ?");
                ps.setString(1, username);
                ps.setString(2, password);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    Map<String, Object> out = new HashMap<>();
                    out.put("id", rs.getLong("id"));
                    out.put("username", username);
                    resp.getWriter().write(gson.toJson(out));
                } else {
                    resp.setStatus(401);
                    resp.getWriter().write("{\"error\":\"Invalid username or password\"}");
                }
                return;
            }

            // ── ONBOARD ──
            if (pathInfo.isEmpty() || pathInfo.equals("/")) {
                Map<String, Object> p = gson.fromJson(req.getReader(), HashMap.class);

                long userId = ((Number) p.get("userId")).longValue();
                int age = p.get("age") != null ? ((Number) p.get("age")).intValue() : 0;
                String goal = (String) p.getOrDefault("goal", "track");
                int defaultPeriodLength = p.get("periodLength") != null
                    ? ((Number) p.get("periodLength")).intValue() : 5;
                int cycleLength = p.get("cycleLength") != null
                    ? ((Number) p.get("cycleLength")).intValue() : 28;
                if (defaultPeriodLength < 1) defaultPeriodLength = 5;
                if (cycleLength < 20 || cycleLength > 45) cycleLength = 28;

                PreparedStatement upd = conn.prepareStatement(
                    "UPDATE users SET age=?, goal=?, period_length=?, cycle_length=? WHERE id=?");
                upd.setInt(1, age);
                upd.setString(2, goal);
                upd.setInt(3, defaultPeriodLength);
                upd.setInt(4, cycleLength);
                upd.setLong(5, userId);
                upd.executeUpdate();

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> periods =
                    (List<Map<String, Object>>) p.get("periods");

                if (periods != null && !periods.isEmpty()) {
                    PreparedStatement del = conn.prepareStatement(
                        "DELETE FROM period_days WHERE user_id=?");
                    del.setLong(1, userId);
                    del.executeUpdate();

                    PreparedStatement ins = conn.prepareStatement(
                        "INSERT IGNORE INTO period_days (user_id, date) VALUES (?, ?)");
                    for (Map<String, Object> period : periods) {
                        String startStr = (String) period.get("startDate");
                        int dur = period.get("duration") != null
                            ? ((Number) period.get("duration")).intValue()
                            : defaultPeriodLength;
                        if (dur < 1) dur = defaultPeriodLength;
                        LocalDate start = LocalDate.parse(startStr);
                        for (int i = 0; i < dur; i++) {
                            ins.setLong(1, userId);
                            ins.setDate(2, Date.valueOf(start.plusDays(i)));
                            ins.addBatch();
                        }
                    }
                    ins.executeBatch();
                }

                Map<String, Object> out = new HashMap<>();
                out.put("id", userId);
                out.put("ok", true);
                resp.getWriter().write(gson.toJson(out));
                return;
            }

            // ── Everything below uses userId ──
            Long userId = userIdFromPath(pathInfo);
            String sub = subPath(pathInfo);

            // ── POST /api/{userId}/periods ──
            if (userId != null && "periods".equals(sub)) {
                Map<String, Object> p = gson.fromJson(req.getReader(), HashMap.class);
                LocalDate date = LocalDate.parse((String) p.get("date"));
                int dur = p.get("duration") != null
                    ? ((Number) p.get("duration")).intValue() : 1;
                if (dur < 1) dur = 1;

                PreparedStatement ps = conn.prepareStatement(
                    "INSERT IGNORE INTO period_days (user_id, date) VALUES (?, ?)");
                for (int i = 0; i < dur; i++) {
                    ps.setLong(1, userId);
                    ps.setDate(2, Date.valueOf(date.plusDays(i)));
                    ps.addBatch();
                }
                ps.executeBatch();

                resp.getWriter().write("{\"ok\":true}");
                return;
            }

            // ── POST /api/{userId}/emotions ──
            if (userId != null && "emotions".equals(sub)) {
                Map<String, String> p = gson.fromJson(req.getReader(), HashMap.class);
                String date = p.get("date");
                String emotion = p.get("emotion");

                PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO emotions (user_id, date, emotion) VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE emotion = VALUES(emotion)");
                ps.setLong(1, userId);
                ps.setDate(2, Date.valueOf(LocalDate.parse(date)));
                ps.setString(3, emotion);
                ps.executeUpdate();

                resp.getWriter().write("{\"ok\":true}");
                return;
            }

            // ── POST /api/{userId}/health-check ──
            if (userId != null && "health-check".equals(sub)) {
                Map<String, Object> p = gson.fromJson(req.getReader(), HashMap.class);
                String date = (String) p.get("date");
                String flow = (String) p.get("flow");
                String pain = (String) p.get("pain");
                Integer periodDuration = p.get("periodDuration") != null
                    ? ((Number) p.get("periodDuration")).intValue() : null;
                Integer cycleLength = p.get("cycleLength") != null
                    ? ((Number) p.get("cycleLength")).intValue() : null;
                String symptoms = (String) p.get("symptoms");

                PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO cycle_health (user_id, date, flow, pain, period_duration, cycle_length, symptoms) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE flow=VALUES(flow), pain=VALUES(pain), " +
                    "period_duration=VALUES(period_duration), cycle_length=VALUES(cycle_length), " +
                    "symptoms=VALUES(symptoms)");
                ps.setLong(1, userId);
                ps.setDate(2, Date.valueOf(LocalDate.parse(date)));
                ps.setString(3, flow);
                ps.setString(4, pain);
                if (periodDuration != null) ps.setInt(5, periodDuration); else ps.setNull(5, java.sql.Types.INTEGER);
                if (cycleLength != null) ps.setInt(6, cycleLength); else ps.setNull(6, java.sql.Types.INTEGER);
                ps.setString(7, symptoms);
                ps.executeUpdate();

                resp.getWriter().write("{\"ok\":true}");
                return;
            }

            // ── POST /api/{userId}/daily-logs ──
            if (userId != null && "daily-logs".equals(sub)) {
                Map<String, String> p = gson.fromJson(req.getReader(), HashMap.class);
                PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO daily_logs (user_id, date, mood, energy, sleep, discharge, flow) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)");
                ps.setLong(1, userId);
                ps.setDate(2, Date.valueOf(LocalDate.parse(p.get("date"))));
                ps.setString(3, p.get("mood"));
                ps.setString(4, p.get("energy"));
                ps.setString(5, p.get("sleep"));
                ps.setString(6, p.get("discharge"));
                ps.setString(7, p.get("flow"));
                ps.executeUpdate();
                resp.getWriter().write("{\"ok\":true}");
                return;
            }

            // ── POST /api/{userId}/symptoms ──
            if (userId != null && "symptoms".equals(sub)) {
                @SuppressWarnings("unchecked")
                List<Map<String, String>> list = gson.fromJson(req.getReader(), List.class);
                PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO symptoms (user_id, date, symptom_name) VALUES (?, ?, ?)");
                for (Map<String, String> s : list) {
                    ps.setLong(1, userId);
                    ps.setDate(2, Date.valueOf(LocalDate.parse(s.get("date"))));
                    ps.setString(3, s.get("symptomName"));
                    ps.addBatch();
                }
                ps.executeBatch();
                resp.getWriter().write("{\"ok\":true}");
                return;
            }

            // ── POST /api/{userId}/cravings ──
            if (userId != null && "cravings".equals(sub)) {
                Map<String, String> p = gson.fromJson(req.getReader(), HashMap.class);
                PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO cravings (user_id, text, created_at) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, userId);
                ps.setString(2, p.get("text"));
                ps.setDate(3, Date.valueOf(LocalDate.now()));
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                Map<String, Object> out = new HashMap<>();
                out.put("id", keys.next() ? keys.getLong(1) : 0);
                out.put("text", p.get("text"));
                out.put("satisfied", false);
                resp.getWriter().write(gson.toJson(out));
                return;
            }

            resp.setStatus(404);
            resp.getWriter().write("{\"error\":\"Not found\"}");

        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    // ══════════════════════════════════════════════════════
    // GET
    // ══════════════════════════════════════════════════════
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setContentType("application/json");

        String pathInfo = req.getPathInfo();
        String sub = subPath(pathInfo);
        Long userId = userIdFromPath(pathInfo);

        if (userId == null) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Invalid user id\"}");
            return;
        }

        try (Connection conn = DBUtil.getConnection()) {

            // ── /profile ──
            if ("profile".equals(sub)) {
                PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM users WHERE id=?");
                ps.setLong(1, userId);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) {
                    resp.setStatus(404);
                    resp.getWriter().write("{\"error\":\"Not found\"}");
                    return;
                }

                PreparedStatement cnt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM period_days WHERE user_id=?");
                cnt.setLong(1, userId);
                ResultSet rsCnt = cnt.executeQuery();
                int periodCount = rsCnt.next() ? rsCnt.getInt(1) : 0;

                Map<String, Object> out = new HashMap<>();
                out.put("id", rs.getLong("id"));
                out.put("username", rs.getString("username"));
                out.put("age", rs.getInt("age"));
                out.put("goal", rs.getString("goal"));
                out.put("periodLength", rs.getInt("period_length"));
                out.put("cycleLength", rs.getInt("cycle_length"));
                out.put("onboarded", rs.getInt("age") > 0 && periodCount > 0);

                resp.getWriter().write(gson.toJson(out));
                return;
            }

            // ── /periods ──
            if ("periods".equals(sub) || sub.isEmpty()) {
                PreparedStatement userPs = conn.prepareStatement(
                    "SELECT period_length, cycle_length FROM users WHERE id=?");
                userPs.setLong(1, userId);
                ResultSet rsUser = userPs.executeQuery();
                int fallbackPeriodLen = 5, fallbackCycleLen = 28;
                if (rsUser.next()) {
                    fallbackPeriodLen = rsUser.getInt("period_length");
                    fallbackCycleLen = rsUser.getInt("cycle_length");
                }

                PreparedStatement ps = conn.prepareStatement(
                    "SELECT date FROM period_days WHERE user_id=? ORDER BY date");
                ps.setLong(1, userId);
                ResultSet rs = ps.executeQuery();
                List<LocalDate> allDays = new ArrayList<>();
                List<String> dayStrings = new ArrayList<>();
                while (rs.next()) {
                    Date d = rs.getDate("date");
                    if (d != null) {
                        allDays.add(d.toLocalDate());
                        dayStrings.add(d.toString());
                    }
                }

                List<Map<String, Object>> ranges = computeRanges(allDays);

                List<Integer> cycleLengths = new ArrayList<>();
                for (int i = 1; i < ranges.size(); i++) {
                    LocalDate prev = LocalDate.parse((String) ranges.get(i-1).get("start"));
                    LocalDate cur = LocalDate.parse((String) ranges.get(i).get("start"));
                    cycleLengths.add((int) ChronoUnit.DAYS.between(prev, cur));
                }

                List<Integer> durations = new ArrayList<>();
                for (Map<String, Object> r : ranges) {
                    durations.add((int) r.get("duration"));
                }

                double avgCycle = computeWeightedCycleAvg(cycleLengths, fallbackCycleLen);

                double avgDuration;
                if (durations.isEmpty()) {
                    avgDuration = fallbackPeriodLen;
                } else {
                    int sum = 0;
                    for (int d : durations) sum += d;
                    avgDuration = (double) sum / durations.size();
                }

                int roundedCycle = (int) Math.round(avgCycle);
                int roundedDuration = (int) Math.round(avgDuration);
                if (roundedCycle < 20) roundedCycle = 20;
                if (roundedCycle > 45) roundedCycle = 45;
                if (roundedDuration < 1) roundedDuration = 1;
                if (roundedDuration > 10) roundedDuration = 10;

                List<Map<String, String>> predictions = new ArrayList<>();
                if (!ranges.isEmpty()) {
                    LocalDate lastStart = LocalDate.parse(
                        (String) ranges.get(ranges.size() - 1).get("start"));
                    LocalDate next = lastStart;
                    for (int c = 0; c < 12; c++) {
                        next = next.plusDays(roundedCycle);
                        Map<String, String> pred = new HashMap<>();
                        pred.put("start", next.toString());
                        pred.put("end", next.plusDays(roundedDuration - 1).toString());
                        predictions.add(pred);
                    }
                }

                int confidence;
                if (cycleLengths.size() >= 6) confidence = 90;
                else if (cycleLengths.size() >= 4) confidence = 75;
                else if (cycleLengths.size() >= 2) confidence = 55;
                else if (cycleLengths.size() == 1) confidence = 35;
                else confidence = 15;

                Map<String, Object> out = new HashMap<>();
                out.put("periodDays", dayStrings);
                out.put("periodLength", fallbackPeriodLen);
                out.put("cycleLength", fallbackCycleLen);
                out.put("ranges", ranges);
                out.put("cycleLengths", cycleLengths);
                out.put("durations", durations);
                out.put("averageCycleLength", roundedCycle);
                out.put("averagePeriodDuration", roundedDuration);
                out.put("cycleCount", cycleLengths.size());
                out.put("confidence", confidence);
                out.put("predictions", predictions);

                resp.getWriter().write(gson.toJson(out));
                return;
            }

            // ── /emotions ──
            if ("emotions".equals(sub)) {
                PreparedStatement ps = conn.prepareStatement(
                    "SELECT date, emotion FROM emotions WHERE user_id=? " +
                    "ORDER BY date DESC LIMIT 60");
                ps.setLong(1, userId);
                ResultSet rs = ps.executeQuery();
                List<Map<String, String>> out = new ArrayList<>();
                while (rs.next()) {
                    Map<String, String> m = new HashMap<>();
                    m.put("date", rs.getDate("date").toString());
                    m.put("emotion", rs.getString("emotion"));
                    out.add(m);
                }
                resp.getWriter().write(gson.toJson(out));
                return;
            }

            // ── /health-check ──
            if ("health-check".equals(sub)) {
                PreparedStatement userPs = conn.prepareStatement(
                    "SELECT period_length, cycle_length FROM users WHERE id=?");
                userPs.setLong(1, userId);
                ResultSet rsUser = userPs.executeQuery();
                int userPeriodLen = 5, userCycleLen = 28;
                if (rsUser.next()) {
                    userPeriodLen = rsUser.getInt("period_length");
                    userCycleLen = rsUser.getInt("cycle_length");
                }

                PreparedStatement ps = conn.prepareStatement(
                    "SELECT date FROM period_days WHERE user_id=? ORDER BY date");
                ps.setLong(1, userId);
                ResultSet rs = ps.executeQuery();
                List<LocalDate> all = new ArrayList<>();
                while (rs.next()) {
                    Date d = rs.getDate("date");
                    if (d != null) all.add(d.toLocalDate());
                }

                List<Map<String, Object>> ranges = computeRanges(all);
                List<Integer> durations = new ArrayList<>();
                for (Map<String, Object> r : ranges) durations.add((int) r.get("duration"));

                List<Integer> cycleLengths = new ArrayList<>();
                for (int i = 1; i < ranges.size(); i++) {
                    LocalDate prev = LocalDate.parse((String) ranges.get(i-1).get("start"));
                    LocalDate cur = LocalDate.parse((String) ranges.get(i).get("start"));
                    cycleLengths.add((int) ChronoUnit.DAYS.between(prev, cur));
                }

                double avgDuration = durations.isEmpty() ? userPeriodLen :
                    durations.stream().mapToInt(Integer::intValue).average().orElse(userPeriodLen);
                // ⭐ Use the SAME weighted average as /periods so numbers match
                double avgCycle = computeWeightedCycleAvg(cycleLengths, userCycleLen);

                int currentDuration = durations.isEmpty() ? 0 : durations.get(durations.size() - 1);
                int currentCycle = cycleLengths.isEmpty() ? 0 : cycleLengths.get(cycleLengths.size() - 1);

                PreparedStatement hp = conn.prepareStatement(
                    "SELECT * FROM cycle_health WHERE user_id=? ORDER BY date DESC LIMIT 1");
                hp.setLong(1, userId);
                ResultSet rsHp = hp.executeQuery();
                Map<String, Object> latestLog = null;
                if (rsHp.next()) {
                    latestLog = new HashMap<>();
                    latestLog.put("date", rsHp.getDate("date").toString());
                    latestLog.put("flow", rsHp.getString("flow"));
                    latestLog.put("pain", rsHp.getString("pain"));
                    latestLog.put("periodDuration", rsHp.getInt("period_duration"));
                    latestLog.put("cycleLength", rsHp.getInt("cycle_length"));
                    latestLog.put("symptoms", rsHp.getString("symptoms"));
                }

                List<Map<String, String>> messages = new ArrayList<>();
                if (currentDuration > 0 && !durations.isEmpty()) {
                    int diff = (int) Math.round(currentDuration - avgDuration);
                    if (Math.abs(diff) >= 2) {
                        Map<String, String> msg = new HashMap<>();
                        msg.put("type", diff > 0 ? "warn" : "info");
                        msg.put("title", "Period duration change");
                        msg.put("text", "Your current period is " + Math.abs(diff) +
                            " day" + (Math.abs(diff) == 1 ? "" : "s") + " " +
                            (diff > 0 ? "longer" : "shorter") +
                            " than your usual average of " + String.format("%.1f", avgDuration) + " days.");
                        messages.add(msg);
                    } else {
                        Map<String, String> msg = new HashMap<>();
                        msg.put("type", "ok");
                        msg.put("title", "Period duration");
                        msg.put("text", "Your period duration is within your usual range.");
                        messages.add(msg);
                    }
                }

                if (currentCycle > 0 && !cycleLengths.isEmpty()) {
                    int diff = (int) Math.round(currentCycle - avgCycle);
                    if (Math.abs(diff) >= 5) {
                        Map<String, String> msg = new HashMap<>();
                        msg.put("type", "warn");
                        msg.put("title", "Cycle length change");
                        msg.put("text", "Your latest cycle was " + Math.abs(diff) +
                            " days " + (diff > 0 ? "longer" : "shorter") +
                            " than your average of " + String.format("%.1f", avgCycle) + " days.");
                        messages.add(msg);
                    }
                }

                if (latestLog != null && "Severe".equals(latestLog.get("pain"))) {
                    Map<String, String> msg = new HashMap<>();
                    msg.put("type", "warn");
                    msg.put("title", "Severe pain reported");
                    msg.put("text", "You logged severe pain. Persistent severe pain may be worth discussing with a healthcare professional.");
                    messages.add(msg);
                }

                if (latestLog != null && "Heavy".equals(latestLog.get("flow"))) {
                    Map<String, String> msg = new HashMap<>();
                    msg.put("type", "warn");
                    msg.put("title", "Heavy flow reported");
                    msg.put("text", "Heavy bleeding can sometimes affect energy levels. If it persists for several cycles, consider talking to a doctor.");
                    messages.add(msg);
                }

                if (currentDuration >= 8) {
                    Map<String, String> msg = new HashMap<>();
                    msg.put("type", "warn");
                    msg.put("title", "Longer than typical period");
                    msg.put("text", "Periods lasting 8 days or more are longer than typical. If this becomes a pattern, it may be worth a check-in with a healthcare professional.");
                    messages.add(msg);
                }

                if (durations.isEmpty() && cycleLengths.isEmpty()) {
                    Map<String, String> msg = new HashMap<>();
                    msg.put("type", "info");
                    msg.put("title", "Keep logging");
                    msg.put("text", "Log a few more cycles to unlock personalised pattern insights.");
                    messages.add(msg);
                }

                Map<String, Object> out = new HashMap<>();
                out.put("avgPeriodDuration", avgDuration);
                out.put("avgCycleLength", avgCycle);
                out.put("currentDuration", currentDuration);
                out.put("currentCycle", currentCycle);
                out.put("totalRanges", ranges.size());
                out.put("messages", messages);
                out.put("latestLog", latestLog);

                resp.getWriter().write(gson.toJson(out));
                return;
            }

            // ── /insights ──
            if ("insights".equals(sub)) {
                PreparedStatement ps = conn.prepareStatement(
                    "SELECT date FROM period_days WHERE user_id=? ORDER BY date");
                ps.setLong(1, userId);
                ResultSet rs = ps.executeQuery();
                List<LocalDate> all = new ArrayList<>();
                while (rs.next()) {
                    Date d = rs.getDate("date");
                    if (d != null) all.add(d.toLocalDate());
                }

                List<Map<String, Object>> ranges = computeRanges(all);
                List<Integer> lengths = new ArrayList<>();
                for (int i = 1; i < ranges.size(); i++) {
                    LocalDate prev = LocalDate.parse((String) ranges.get(i-1).get("start"));
                    LocalDate cur = LocalDate.parse((String) ranges.get(i).get("start"));
                    lengths.add((int) ChronoUnit.DAYS.between(prev, cur));
                }

                double avg = lengths.isEmpty() ? 0 :
                    lengths.stream().mapToInt(Integer::intValue).average().orElse(0);
                int min = lengths.isEmpty() ? 0 :
                    lengths.stream().mapToInt(Integer::intValue).min().orElse(0);
                int max = lengths.isEmpty() ? 0 :
                    lengths.stream().mapToInt(Integer::intValue).max().orElse(0);

                Map<String, Object> out = new HashMap<>();
                out.put("totalPeriods", ranges.size());
                out.put("avgCycleLength", avg);
                out.put("minCycle", min);
                out.put("maxCycle", max);
                resp.getWriter().write(gson.toJson(out));
                return;
            }

            // ── /cravings ──
            if ("cravings".equals(sub)) {
                PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM cravings WHERE user_id=? ORDER BY id DESC");
                ps.setLong(1, userId);
                ResultSet rs = ps.executeQuery();
                List<Map<String, Object>> out = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("text", rs.getString("text"));
                    m.put("satisfied", rs.getBoolean("satisfied"));
                    out.add(m);
                }
                resp.getWriter().write(gson.toJson(out));
                return;
            }

            resp.setStatus(404);
            resp.getWriter().write("{\"error\":\"Not found\"}");

        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    // ══════════════════════════════════════════════════════
    // DELETE
    // ══════════════════════════════════════════════════════
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        String pathInfo = req.getPathInfo();
        String sub = subPath(pathInfo);
        Long userId = userIdFromPath(pathInfo);

        if (userId == null) { resp.setStatus(400); return; }

        try (Connection conn = DBUtil.getConnection()) {
            // DELETE /api/{userId}/periods/{date}
            if ("periods".equals(sub)) {
                String[] parts = pathInfo.split("/");
                if (parts.length < 4) { resp.setStatus(400); return; }
                LocalDate date = LocalDate.parse(parts[3]);
                PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM period_days WHERE user_id=? AND date=?");
                ps.setLong(1, userId);
                ps.setDate(2, Date.valueOf(date));
                ps.executeUpdate();
                resp.setStatus(204);
                return;
            }

            // DELETE /api/{userId}/emotions/{date}
            if ("emotions".equals(sub)) {
                String[] parts = pathInfo.split("/");
                if (parts.length < 4) { resp.setStatus(400); return; }
                LocalDate date = LocalDate.parse(parts[3]);
                PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM emotions WHERE user_id=? AND date=?");
                ps.setLong(1, userId);
                ps.setDate(2, Date.valueOf(date));
                ps.executeUpdate();
                resp.setStatus(204);
                return;
            }

            // DELETE /api/{userId}/cravings/{id}
            if ("cravings".equals(sub)) {
                String[] parts = pathInfo.split("/");
                if (parts.length < 4) { resp.setStatus(400); return; }
                long cid = Long.parseLong(parts[3]);
                PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM cravings WHERE id=? AND user_id=?");
                ps.setLong(1, cid);
                ps.setLong(2, userId);
                ps.executeUpdate();
                resp.setStatus(204);
                return;
            }
            resp.setStatus(404);
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(500);
        }
    }

    // ══════════════════════════════════════════════════════
    // PUT
    // ══════════════════════════════════════════════════════
    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setContentType("application/json");
        String pathInfo = req.getPathInfo();
        String sub = subPath(pathInfo);
        Long userId = userIdFromPath(pathInfo);

        if (userId == null) { resp.setStatus(400); return; }

        try (Connection conn = DBUtil.getConnection()) {

            // ── PUT /api/{userId}/settings ──
            if ("settings".equals(sub)) {
                Map<String, Object> p = gson.fromJson(req.getReader(), HashMap.class);
                Integer cycleLength = p.get("cycleLength") != null
                    ? ((Number) p.get("cycleLength")).intValue() : null;
                Integer periodLength = p.get("periodLength") != null
                    ? ((Number) p.get("periodLength")).intValue() : null;

                if (cycleLength != null) {
                    if (cycleLength < 20 || cycleLength > 45) {
                        resp.setStatus(400);
                        resp.getWriter().write("{\"error\":\"Cycle length must be 20-45\"}");
                        return;
                    }
                    PreparedStatement ps = conn.prepareStatement(
                        "UPDATE users SET cycle_length=? WHERE id=?");
                    ps.setInt(1, cycleLength);
                    ps.setLong(2, userId);
                    ps.executeUpdate();
                }
                if (periodLength != null) {
                    if (periodLength < 1 || periodLength > 15) {
                        resp.setStatus(400);
                        resp.getWriter().write("{\"error\":\"Period length must be 1-15\"}");
                        return;
                    }
                    PreparedStatement ps = conn.prepareStatement(
                        "UPDATE users SET period_length=? WHERE id=?");
                    ps.setInt(1, periodLength);
                    ps.setLong(2, userId);
                    ps.executeUpdate();
                }
                resp.getWriter().write("{\"ok\":true}");
                return;
            }

            // ── PUT /api/{userId}/cravings/{id}/toggle ──
            if ("cravings".equals(sub)) {
                String[] parts = pathInfo.split("/");
                if (parts.length < 5 || !"toggle".equals(parts[4])) {
                    resp.setStatus(400); return;
                }
                long cid = Long.parseLong(parts[3]);
                PreparedStatement ps = conn.prepareStatement(
                    "UPDATE cravings SET satisfied = NOT satisfied WHERE id=? AND user_id=?");
                ps.setLong(1, cid);
                ps.setLong(2, userId);
                ps.executeUpdate();
                resp.getWriter().write("{\"ok\":true}");
                return;
            }

            resp.setStatus(404);
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}