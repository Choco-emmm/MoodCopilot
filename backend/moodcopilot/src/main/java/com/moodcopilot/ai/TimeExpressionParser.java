package com.moodcopilot.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 中文时间表达式解析器。从用户输入中检测常见时间词，返回 Unix 秒级时间范围。
 * 纯正则匹配，无 LLM 调用，延迟为零。
 */
public final class TimeExpressionParser {

    private static final Logger log = LoggerFactory.getLogger(TimeExpressionParser.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public record TimeRange(long fromTimestamp, long toTimestamp) {}

    private TimeExpressionParser() {}

    // ── 公共入口 ──

    public static Optional<TimeRange> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        List<String> candidates = new ArrayList<>();
        for (Rule rule : RULES) {
            Matcher m = rule.pattern.matcher(text);
            if (m.find()) {
                TimeRange tr = rule.calculator.calc(m);
                String label = rule.label;
                // log.info("时间表达式匹配 text=\"{}\" pattern=\"{}\" from={} ({}) to={} ({})", // text 包含用户聊天消息，已注释
                //         truncate(text, 80), label,
                //         tr.fromTimestamp, formatDateTime(tr.fromTimestamp),
                //         tr.toTimestamp, formatDateTime(tr.toTimestamp));
                log.info("时间表达式匹配 pattern=\"{}\" from={} ({}) to={} ({})",
                        label,
                        tr.fromTimestamp, formatDateTime(tr.fromTimestamp),
                        tr.toTimestamp, formatDateTime(tr.toTimestamp));
                if (candidates.size() > 1) {
                    // log.info("多个时间表达式匹配 text=\"{}\" candidates={} 采用第一个: {}", // text 包含用户聊天消息，已注释
                    //         truncate(text, 80), candidates, label);
                    log.info("多个时间表达式匹配 candidates={} 采用第一个: {}", candidates, label);
                }
                return Optional.of(tr);
            }
        }
        return Optional.empty();
    }

    // ── 工具 ──

    static String formatDateTime(long epochSecond) {
        if (epochSecond >= 253402271999L) { // Year 9999
            return "未来";
        }
        if (epochSecond <= 0) {
            return "过去";
        }
        return Instant.ofEpochSecond(epochSecond).atZone(ZONE).format(DT_FMT);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    // ── 时间计算辅助 ──

    private static long toEpoch(ZonedDateTime zdt) {
        return zdt.toEpochSecond();
    }

    private static ZonedDateTime now() {
        return ZonedDateTime.now(ZONE);
    }

    private static ZonedDateTime todayStart() {
        return now().truncatedTo(ChronoUnit.DAYS);
    }

    private static ZonedDateTime yesterdayStart() {
        return todayStart().minusDays(1);
    }

    private static ZonedDateTime thisWeekStart() {
        return todayStart().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static ZonedDateTime thisMonthStart() {
        return todayStart().withDayOfMonth(1);
    }

    private static ZonedDateTime thisYearStart() {
        return todayStart().withDayOfYear(1);
    }

    // ── 规则定义 ──

    private interface TimeCalc {
        TimeRange calc(Matcher m);
    }

    private record Rule(String label, Pattern pattern, TimeCalc calculator) {}

    private static final List<Rule> RULES = new ArrayList<>();

    static {
        // 优先级从高到低

        // 今天
        RULES.add(new Rule("今天", Pattern.compile("今\\s*天"), m ->
                new TimeRange(toEpoch(todayStart()), toEpoch(now()))));

        // 昨天
        RULES.add(new Rule("昨天", Pattern.compile("昨\\s*天"), m ->
                new TimeRange(toEpoch(yesterdayStart()), toEpoch(todayStart()))));

        // 前天
        RULES.add(new Rule("前天", Pattern.compile("前\\s*天"), m ->
                new TimeRange(toEpoch(todayStart().minusDays(2)), toEpoch(yesterdayStart()))));

        // 最近 N 天 / 最近 N 个天
        RULES.add(new Rule("最近N天", Pattern.compile("最\\s*近\\s*(\\d+)\\s*(个)?\\s*天"), m -> {
            int n = Integer.parseInt(m.group(1));
            return new TimeRange(toEpoch(now().minusDays(n)), toEpoch(now()));
        }));

        // 过去 N 天
        RULES.add(new Rule("过去N天", Pattern.compile("过\\s*去\\s*(\\d+)\\s*(个)?\\s*天"), m -> {
            int n = Integer.parseInt(m.group(1));
            return new TimeRange(toEpoch(now().minusDays(n)), toEpoch(now()));
        }));

        // N 天前 / N 天以前
        RULES.add(new Rule("N天前", Pattern.compile("(\\d+)\\s*(个)?\\s*天\\s*(以\\s*)?前"), m -> {
            int n = Integer.parseInt(m.group(1));
            return new TimeRange(toEpoch(now().minusDays(n).truncatedTo(ChronoUnit.DAYS)),
                    toEpoch(now().minusDays(n - 1).truncatedTo(ChronoUnit.DAYS)));
        }));

        // 最近 N 周 / 最近 N 个星期
        RULES.add(new Rule("最近N周", Pattern.compile("最\\s*近\\s*(\\d+)\\s*(个\\s*)?(星\\s*期|周)"), m -> {
            int n = Integer.parseInt(m.group(1));
            return new TimeRange(toEpoch(now().minusWeeks(n)), toEpoch(now()));
        }));

        // 过去 N 周 / 过去 N 个星期
        RULES.add(new Rule("过去N周", Pattern.compile("过\\s*去\\s*(\\d+)\\s*(个\\s*)?(星\\s*期|周)"), m -> {
            int n = Integer.parseInt(m.group(1));
            return new TimeRange(toEpoch(now().minusWeeks(n)), toEpoch(now()));
        }));

        // N 周前 / N 个星期前
        RULES.add(new Rule("N周前", Pattern.compile("(\\d+)\\s*(个\\s*)?(星\\s*期|周)\\s*(以\\s*)?前"), m -> {
            int n = Integer.parseInt(m.group(1));
            return new TimeRange(toEpoch(now().minusWeeks(n)), toEpoch(now()));
        }));

        // 最近 N 个月 / 最近 N 个月
        RULES.add(new Rule("最近N个月", Pattern.compile("最\\s*近\\s*(\\d+)\\s*(个\\s*)?月"), m -> {
            int n = Integer.parseInt(m.group(1));
            return new TimeRange(toEpoch(now().minusMonths(n)), toEpoch(now()));
        }));

        // 过去 N 个月
        RULES.add(new Rule("过去N个月", Pattern.compile("过\\s*去\\s*(\\d+)\\s*(个\\s*)?月"), m -> {
            int n = Integer.parseInt(m.group(1));
            return new TimeRange(toEpoch(now().minusMonths(n)), toEpoch(now()));
        }));

        // N 个月前
        RULES.add(new Rule("N个月前", Pattern.compile("(\\d+)\\s*(个\\s*)?月\\s*(以\\s*)?前"), m -> {
            int n = Integer.parseInt(m.group(1));
            return new TimeRange(toEpoch(now().minusMonths(n)), toEpoch(now()));
        }));

        // 过去半年 / 过去一年
        RULES.add(new Rule("过去半年", Pattern.compile("过\\s*去\\s*半\\s*年"), m ->
                new TimeRange(toEpoch(now().minusMonths(6)), toEpoch(now()))));

        RULES.add(new Rule("过去一年", Pattern.compile("过\\s*去\\s*(一\\s*)?年"), m ->
                new TimeRange(toEpoch(now().minusYears(1)), toEpoch(now()))));

        // 这周 / 本周
        RULES.add(new Rule("这周", Pattern.compile("(这|本)\\s*(个\\s*)?(星\\s*期|周)"), m ->
                new TimeRange(toEpoch(thisWeekStart()), toEpoch(now()))));

        // 上周 / 上个星期
        RULES.add(new Rule("上周", Pattern.compile("上\\s*(个\\s*)?(星\\s*期|周)"), m ->
                new TimeRange(toEpoch(thisWeekStart().minusWeeks(1)), toEpoch(thisWeekStart()))));

        // 上上周
        RULES.add(new Rule("上上周", Pattern.compile("上\\s*上\\s*(个\\s*)?(星\\s*期|周)"), m ->
                new TimeRange(toEpoch(thisWeekStart().minusWeeks(2)), toEpoch(thisWeekStart().minusWeeks(1)))));

        // 本月 / 这个月
        RULES.add(new Rule("本月", Pattern.compile("(这|本)\\s*(个\\s*)?月"), m ->
                new TimeRange(toEpoch(thisMonthStart()), toEpoch(now()))));

        // 上个月 / 上月
        RULES.add(new Rule("上个月", Pattern.compile("上\\s*(个\\s*)?月"), m ->
                new TimeRange(toEpoch(thisMonthStart().minusMonths(1)), toEpoch(thisMonthStart()))));

        // 上上个月
        RULES.add(new Rule("上上个月", Pattern.compile("上\\s*上\\s*(个\\s*)?月"), m ->
                new TimeRange(toEpoch(thisMonthStart().minusMonths(2)), toEpoch(thisMonthStart().minusMonths(1)))));

        // 今年
        RULES.add(new Rule("今年", Pattern.compile("今\\s*年"), m ->
                new TimeRange(toEpoch(thisYearStart()), toEpoch(now()))));

        // 去年
        RULES.add(new Rule("去年", Pattern.compile("去\\s*年"), m ->
                new TimeRange(toEpoch(thisYearStart().minusYears(1)), toEpoch(thisYearStart()))));

        // 前年
        RULES.add(new Rule("前年", Pattern.compile("前\\s*年"), m ->
                new TimeRange(toEpoch(thisYearStart().minusYears(2)), toEpoch(thisYearStart().minusYears(1)))));

        // 前几周
        RULES.add(new Rule("前几周", Pattern.compile("前\\s*几\\s*(个\\s*)?(星\\s*期|周)"), m ->
                new TimeRange(toEpoch(now().minusWeeks(3)), toEpoch(now()))));

        // 前几个月
        RULES.add(new Rule("前几个月", Pattern.compile("前\\s*几\\s*(个\\s*)?月"), m ->
                new TimeRange(toEpoch(now().minusMonths(3)), toEpoch(now()))));

        // 前几天
        RULES.add(new Rule("前几天", Pattern.compile("前\\s*几\\s*天"), m ->
                new TimeRange(toEpoch(now().minusDays(7)), toEpoch(now()))));

        // 前几年
        RULES.add(new Rule("前几年", Pattern.compile("前\\s*几\\s*年"), m ->
                new TimeRange(toEpoch(now().minusYears(3)), toEpoch(now()))));

        // 这几天
        RULES.add(new Rule("这几天", Pattern.compile("这\\s*几\\s*天"), m ->
                new TimeRange(toEpoch(now().minusDays(7)), toEpoch(now()))));

        // 这几个月
        RULES.add(new Rule("这几个月", Pattern.compile("这\\s*几\\s*(个\\s*)?月"), m ->
                new TimeRange(toEpoch(now().minusMonths(3)), toEpoch(now()))));

        // 这阵子
        RULES.add(new Rule("这阵子", Pattern.compile("这\\s*阵\\s*(子\\s*)?"), m ->
                new TimeRange(toEpoch(now().minusDays(14)), toEpoch(now()))));

        // 最近（单独，放最后保底 — 必须在具体"最近N天"等模式之后）
        RULES.add(new Rule("最近", Pattern.compile("最\\s*近"), m ->
                new TimeRange(toEpoch(now().minusDays(14)), toEpoch(now()))));
    }
}
