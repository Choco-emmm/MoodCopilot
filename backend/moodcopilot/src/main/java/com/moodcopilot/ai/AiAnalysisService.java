package com.moodcopilot.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.diary.DiaryAnalysis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AiAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisService.class);

    private static final String SYSTEM_PROMPT = """
            You are a compassionate emotion analysis assistant. Analyze the following diary entry and return ONLY valid JSON (no markdown, no explanation). The JSON must have these exact fields:
            - moodLabel: one of [喜悦, 期待, 兴奋, 自豪, 轻松, 平静, 感恩, 满足, 烦躁, 愤怒, 焦虑, 害怕, 疲惫, 委屈, 难过, 孤独, 迷茫, 内疚]
            - moodIntensity: integer 1-5, anchored as:
              1 = extremely mild / fleeting, barely noticeable
              2 = faintly present / background emotion
              3 = clearly felt / affecting current attention
              4 = strong / driving physiological reactions or behavior
              5 = overwhelming / hard to control or bear
            - secondaryMoods: OPTIONAL array of strings from the same mood list above. Include only if the diary clearly expresses more than one emotion. Return empty array [] when the emotion is singular.
            - topicLabels: array of strings from [人际关系, 工作学习, 睡眠身体, 自我成长, 日常情绪]
            - summary: brief Chinese summary, max 48 characters
            - feedback: gentle, compassionate Chinese feedback, max 200 characters
            """;

    private static final String WEEKLY_SYSTEM_PROMPT = """
            You are a compassionate weekly reflection assistant. Below is a list of diary entries from the past week, each with its primary mood, optional secondary moods, topic, and summary. Write a warm, gentle Chinese reflection (150-300 characters) that:
            1. Acknowledges the emotional journey of the week, noticing when emotions were mixed or layered
            2. Notices patterns or shifts in mood and themes, including subtle secondary emotions that may signal underlying currents
            3. Offers gentle encouragement without being preachy
            Return ONLY the Chinese text. You are encouraged to use simple Markdown (like **bold**, lists, and line breaks) for a beautiful and clear layout. No JSON, no explanation.""";

    private final ChatClient analysisChatClient;
    private final ObjectMapper objectMapper;

    public AiAnalysisService(ChatClient analysisChatClient, ObjectMapper objectMapper) {
        this.analysisChatClient = analysisChatClient;
        this.objectMapper = objectMapper;
    }

    public DiaryAnalysis analyze(String content) {
        try {
            String json = analysisChatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(content)
                    .call()
                    .content();
            return parseAiResponse(json);
        } catch (Exception e) {
            log.warn("AI analysis failed, falling back to keyword analysis: {}", e.getMessage());
            return keywordAnalyze(content);
        }
    }

    @SuppressWarnings("unchecked")
    private DiaryAnalysis parseAiResponse(String json) throws JsonProcessingException {
        Map<String, Object> map = objectMapper.readValue(json, Map.class);
        String moodLabel = (String) map.get("moodLabel");
        int moodIntensity = ((Number) map.get("moodIntensity")).intValue();
        List<String> topicLabels = (List<String>) map.get("topicLabels");
        List<String> secondaryMoods = (List<String>) map.get("secondaryMoods");
        String summary = (String) map.get("summary");
        String feedback = (String) map.get("feedback");
        List<String> safeSecondary = (secondaryMoods != null) ? secondaryMoods : List.of();
        return new DiaryAnalysis(moodLabel, Math.min(5, Math.max(1, moodIntensity)),
                topicLabels, safeSecondary, summary, feedback);
    }

    // ── Weekly report ──

    public String generateWeeklySummary(List<String> diaryContents, List<DiaryAnalysis> analyses) {
        if (diaryContents.isEmpty())
            return "本周还没有记录日记，去写一篇吧～";

        StringBuilder prompt = new StringBuilder("本周日记摘要：\n");
        for (int i = 0; i < diaryContents.size(); i++) {
            DiaryAnalysis a = i < analyses.size() ? analyses.get(i) : null;
            prompt.append("- ");
            if (a != null) {
                prompt.append("情绪：").append(a.moodLabel());
                if (a.hasSecondaryMoods()) {
                    prompt.append("（同时感受到：").append(String.join("、", a.secondaryMoods())).append("）");
                }
                prompt.append("，强度：").append(a.moodIntensity())
                        .append("，主题：").append(String.join("、", a.topicLabels()))
                        .append("，摘要：").append(a.summary());
            } else {
                String content = diaryContents.get(i);
                prompt.append(content.length() > 60 ? content.substring(0, 60) + "..." : content);
            }
            prompt.append("\n");
        }

        try {
            return analysisChatClient.prompt()
                    .system(WEEKLY_SYSTEM_PROMPT)
                    .user(prompt.toString())
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("AI weekly summary failed, falling back: {}", e.getMessage());
            return fallbackWeeklySummary(diaryContents.size(), analyses);
        }
    }

    public ReportGuidance generateWeeklyGuidance(List<String> diaryContents, List<DiaryAnalysis> analyses) {
        return generateReportGuidance("本周", diaryContents, analyses);
    }

    public ReportGuidance generateCustomGuidance(String period, List<String> diaryContents,
            List<DiaryAnalysis> analyses) {
        return generateReportGuidance(period, diaryContents, analyses);
    }

    // ── Monthly report ──

    private static final String MONTHLY_SYSTEM_PROMPT = """
            You are a compassionate monthly reflection assistant. Below is a list of diary entries from the past month, each with its primary mood, optional secondary moods, topic, and summary. Write a warm, gentle Chinese reflection (200-400 characters) that:
            1. Acknowledges the emotional journey of the month, noticing when emotions were layered or contradictory
            2. Notices patterns, shifts, or trends in mood and themes over the longer period, paying attention to the interplay between primary and secondary emotions
            3. Offers gentle encouragement and a forward-looking perspective
            Return ONLY the Chinese text. You are encouraged to use simple Markdown (like **bold**, lists, and line breaks) for a beautiful and clear layout. No JSON, no explanation.""";

    public String generateMonthlySummary(List<String> diaryContents, List<DiaryAnalysis> analyses) {
        if (diaryContents.isEmpty())
            return "本月还没有记录日记，去写一篇吧～";

        StringBuilder prompt = new StringBuilder("本月日记摘要：\n");
        for (int i = 0; i < diaryContents.size(); i++) {
            DiaryAnalysis a = i < analyses.size() ? analyses.get(i) : null;
            prompt.append("- ");
            if (a != null) {
                prompt.append("情绪：").append(a.moodLabel());
                if (a.hasSecondaryMoods()) {
                    prompt.append("（同时感受到：").append(String.join("、", a.secondaryMoods())).append("）");
                }
                prompt.append("，强度：").append(a.moodIntensity())
                        .append("，主题：").append(String.join("、", a.topicLabels()))
                        .append("，摘要：").append(a.summary());
            } else {
                String content = diaryContents.get(i);
                prompt.append(content.length() > 60 ? content.substring(0, 60) + "..." : content);
            }
            prompt.append("\n");
        }

        try {
            return analysisChatClient.prompt()
                    .system(MONTHLY_SYSTEM_PROMPT)
                    .user(prompt.toString())
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("AI monthly summary failed, falling back: {}", e.getMessage());
            return fallbackMonthlySummary(diaryContents.size(), analyses);
        }
    }

    public ReportGuidance generateMonthlyGuidance(List<String> diaryContents, List<DiaryAnalysis> analyses) {
        return generateReportGuidance("本月", diaryContents, analyses);
    }

    private static final String REPORT_GUIDANCE_SYSTEM_PROMPT = """
            You are MoodCopilot. Based on the user's diary patterns, return ONLY valid JSON with:
            - insights: array of 2-3 concise Chinese observations about emotional patterns (consider both primary and secondary moods for deeper insight)
            - suggestions: array of 2-3 small, concrete Chinese actions the user can try
            - followUpPrompt: one Chinese sentence the user could ask MoodCopilot to explore further
            Be warm and specific. Do not diagnose. You can use simple Markdown (like **bold**) inside the strings for emphasis. Do not use emoji.""";

    @SuppressWarnings("unchecked")
    private ReportGuidance generateReportGuidance(String period, List<String> diaryContents,
            List<DiaryAnalysis> analyses) {
        if (diaryContents.isEmpty()) {
            return new ReportGuidance(List.of(), List.of(), "等你多记录几天，我们再一起看看变化。");
        }
        StringBuilder prompt = new StringBuilder(period).append("日记模式：\n");
        for (int i = 0; i < diaryContents.size(); i++) {
            DiaryAnalysis analysis = i < analyses.size() ? analyses.get(i) : null;
            prompt.append("- ");
            if (analysis != null) {
                prompt.append("情绪：").append(analysis.moodLabel());
                if (analysis.hasSecondaryMoods()) {
                    prompt.append("（同时：").append(String.join("、", analysis.secondaryMoods())).append("）");
                }
                prompt.append("，强度：").append(analysis.moodIntensity())
                        .append("，主题：").append(String.join("、", analysis.topicLabels()))
                        .append("，摘要：").append(analysis.summary());
            } else {
                String content = diaryContents.get(i);
                prompt.append(content.length() > 80 ? content.substring(0, 80) + "..." : content);
            }
            prompt.append("\n");
        }
        try {
            String json = analysisChatClient.prompt()
                    .system(REPORT_GUIDANCE_SYSTEM_PROMPT)
                    .user(prompt.toString())
                    .call()
                    .content();
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            return new ReportGuidance(
                    sanitizeStringList((List<Object>) map.get("insights"), fallbackInsights(analyses)),
                    sanitizeStringList((List<Object>) map.get("suggestions"), fallbackSuggestions(analyses)),
                    sanitizeString((String) map.get("followUpPrompt"), fallbackFollowUp(analyses)));
        } catch (Exception e) {
            log.warn("AI report guidance failed, falling back: {}", e.getMessage());
            return fallbackGuidance(analyses);
        }
    }

    private ReportGuidance fallbackGuidance(List<DiaryAnalysis> analyses) {
        return new ReportGuidance(fallbackInsights(analyses), fallbackSuggestions(analyses),
                fallbackFollowUp(analyses));
    }

    private List<String> fallbackInsights(List<DiaryAnalysis> analyses) {
        String topMood = topMood(analyses);
        String topTopic = topTopic(analyses);
        return List.of(
                "最近比较常出现的情绪是「" + topMood + "」。",
                "情绪内容更多和「" + topTopic + "」有关。");
    }

    private List<String> fallbackSuggestions(List<DiaryAnalysis> analyses) {
        return List.of(
                "今天先给自己 10 分钟不被打扰的时间，慢慢把状态放下来。",
                "下次记录时，可以多写一句「这件事真正影响我的地方是？」");
    }

    private String fallbackFollowUp(List<DiaryAnalysis> analyses) {
        return "我想继续聊聊最近的「" + topMood(analyses) + "」从哪里来。";
    }

    private String topMood(List<DiaryAnalysis> analyses) {
        Map<String, Double> weighted = new HashMap<>();
        for (DiaryAnalysis a : analyses) {
            if (a == null || a.moodLabel() == null) continue;
            weighted.merge(a.moodLabel(), 1.0, Double::sum);
            if (a.secondaryMoods() != null) {
                for (String s : a.secondaryMoods()) {
                    weighted.merge(s, 0.5, Double::sum);
                }
            }
        }
        return weighted.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("复杂");
    }

    private String topTopic(List<DiaryAnalysis> analyses) {
        return analyses.stream()
                .filter(a -> a != null && a.topicLabels() != null)
                .flatMap(a -> a.topicLabels().stream())
                .collect(Collectors.groupingBy(topic -> topic, Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("日常情绪");
    }

    private List<String> sanitizeStringList(List<Object> values, List<String> fallback) {
        if (values == null || values.isEmpty())
            return fallback;
        List<String> result = values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .limit(3)
                .toList();
        return result.isEmpty() ? fallback : result;
    }

    private String sanitizeString(String value, String fallback) {
        if (value == null || value.isBlank())
            return fallback;
        return value.trim();
    }

    public record ReportGuidance(
            List<String> insights,
            List<String> suggestions,
            String followUpPrompt) {
    }

    private String fallbackMonthlySummary(int count, List<DiaryAnalysis> analyses) {
        if (count == 0)
            return "本月还没有记录日记，去写一篇吧～";
        String topMood = topMood(analyses);
        return "本月共记录了 " + count + " 篇日记，主要情绪为「" + topMood + "」。一个月的坚持不容易，继续记录，你会看见自己的成长轨迹。";
    }

    // ── Coaching plan ──

    private static final String COACHING_SYSTEM_PROMPT = """
            You are a compassionate emotional wellness coach. Below are the user's recent diary entries with primary moods, optional secondary moods, and topics. Write a gentle, personalized Chinese coaching suggestion (100-200 characters) that:
            1. Acknowledges their recent emotional patterns, noticing when primary and secondary moods reveal layered feelings
            2. Suggests one small, concrete action they could try today
            3. Is encouraging but not preachy
            Return ONLY the Chinese text. You can use simple Markdown (like **bold**) to highlight key actionable advice. No JSON, no explanation.""";

    public String generateCoaching(List<String> contents, List<DiaryAnalysis> analyses) {
        if (contents.isEmpty())
            return "还没有足够的日记数据，多记录几天后我会为你生成陪跑建议。";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < contents.size(); i++) {
            DiaryAnalysis a = i < analyses.size() ? analyses.get(i) : null;
            if (a != null) {
                sb.append("情绪：").append(a.moodLabel());
                if (a.hasSecondaryMoods()) {
                    sb.append("（同时：").append(String.join("、", a.secondaryMoods())).append("）");
                }
                sb.append("，主题：").append(String.join("、", a.topicLabels())).append("\n");
            }
        }
        try {
            return analysisChatClient.prompt()
                    .system(COACHING_SYSTEM_PROMPT)
                    .user(sb.toString())
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("AI coaching failed: {}", e.getMessage());
            String topMood = topMood(analyses);
            return "你最近的情绪以「" + topMood + "」为主。试着每天给自己5分钟安静时间，不用想任何事，只是呼吸。";
        }
    }

    // ── User chat context ──

    private static final String USER_CONTEXT_SYSTEM_PROMPT = """
            你是用户长期背景总结助手。请将"已有用户背景"和"本次新日记"融合成新的用户专属背景，用于后续聊天。
            要求：
            1) 中文，120-220字；
            2) 只保留稳定、可帮助理解用户的关键信息（常见情绪（含主次情绪）、触发主题、近期变化、偏好表达方式）；
            3) 不要复述过多细节，不要逐条罗列历史日记，不要输出建议清单；
            4) 输出纯文本，不要 markdown、不要 JSON。
            """;

    public String generateUserContext(String previousContext, String diaryContent, DiaryAnalysis analysis) {
        String oldContext = previousContext == null ? "" : previousContext.trim();
        if (oldContext.length() > 400) {
            oldContext = oldContext.substring(0, 400);
        }
        String content = diaryContent == null ? "" : diaryContent.trim();
        if (content.length() > 280) {
            content = content.substring(0, 280);
        }

        String analysisLine = "";
        if (analysis != null) {
            String topics = (analysis.topicLabels() == null || analysis.topicLabels().isEmpty())
                    ? "日常情绪"
                    : String.join("、", analysis.topicLabels());
            analysisLine = "情绪=" + analysis.moodLabel() +
                    "；强度=" + analysis.moodIntensity() +
                    "；主题=" + topics;
            if (analysis.hasSecondaryMoods()) {
                analysisLine += "；次要情绪=" + String.join("、", analysis.secondaryMoods());
            }
        }

        String prompt = "已有用户背景：\n" + (oldContext.isBlank() ? "（空）" : oldContext)
                + "\n\n本次新日记：\n" + content
                + (analysisLine.isBlank() ? "" : "\n结构化分析：" + analysisLine);

        try {
            String merged = analysisChatClient.prompt()
                    .system(USER_CONTEXT_SYSTEM_PROMPT)
                    .user(prompt)
                    .call()
                    .content();
            if (merged == null || merged.isBlank()) {
                return fallbackUserContext(oldContext, content, analysis);
            }
            String normalized = merged.trim();
            return normalized.length() > 240 ? normalized.substring(0, 240) : normalized;
        } catch (Exception e) {
            log.warn("AI user context generation failed: {}", e.getMessage());
            return fallbackUserContext(oldContext, content, analysis);
        }
    }

    private String fallbackUserContext(String previousContext, String content, DiaryAnalysis analysis) {
        String mood = analysis != null && analysis.moodLabel() != null ? analysis.moodLabel() : "复杂";
        String topics = analysis != null && analysis.topicLabels() != null && !analysis.topicLabels().isEmpty()
                ? String.join("、", analysis.topicLabels())
                : "日常情绪";
        String snippet = content.isBlank() ? "" : (content.length() > 60 ? content.substring(0, 60) + "..." : content);
        String moodPart = "近期主要情绪偏向「" + mood + "」";
        if (analysis != null && analysis.hasSecondaryMoods()) {
            moodPart += "（同时伴随「" + String.join("、", analysis.secondaryMoods()) + "」）";
        }
        String merged = (previousContext == null || previousContext.isBlank() ? "" : previousContext + " ")
                + moodPart + "，高频主题是「" + topics + "」。"
                + (snippet.isBlank() ? "" : "最新记录提到：" + snippet);
        return merged.length() > 240 ? merged.substring(0, 240) : merged;
    }

    // ── Community mood ──

    public Map<String, Integer> communityMood(List<String> moodLabels) {
        return moodLabels.stream()
                .filter(m -> m != null)
                .collect(
                        java.util.stream.Collectors.groupingBy(m -> m, java.util.stream.Collectors.summingInt(m -> 1)));
    }

    // ── Encouragement generation ──

    private static final String ENCOURAGEMENT_SYSTEM_PROMPT = """
            You are a warm, compassionate stranger. Below is a diary entry. Generate exactly 3 short, anonymous encouragement messages in Chinese, each under 60 characters. They should be gentle, specific (reference the diary content), and feel like a real person wrote them, not a therapist. Format your response as a JSON array of 3 strings, nothing else.
            Example: ["抱抱你，摔倒了没关系，明天又是新的一天","减肥真的好难，但你已经在努力了","我也有过类似的委屈，想说你不是一个人"]""";

    public List<String> generateEncouragements(String diaryContent) {
        try {
            String response = analysisChatClient.prompt()
                    .system(ENCOURAGEMENT_SYSTEM_PROMPT)
                    .user(diaryContent)
                    .call()
                    .content();
            return objectMapper.readValue(response, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("AI encouragement generation failed: {}", e.getMessage());
            return fallbackEncouragements();
        }
    }

    private List<String> fallbackEncouragements() {
        return List.of(
                "看到你了，今天辛苦了",
                "你的感受很重要，谢谢你的分享",
                "你不是一个人，有我在听");
    }

    private String fallbackWeeklySummary(int count, List<DiaryAnalysis> analyses) {
        if (count == 0)
            return "本周还没有记录日记，去写一篇吧～";

        String topMood = topMood(analyses);
        return String.format("本周共记录了 %d 篇日记，主要情绪为「%s」。继续记录，你会慢慢看清自己的节奏。", count, topMood);
    }

    // ══════════════════════════════════════════════
    // Keyword-based fallback (expanded taxonomy)
    // ══════════════════════════════════════════════

    private DiaryAnalysis keywordAnalyze(String content) {
        String mood = pickMood(content);
        List<String> topics = pickTopics(content);
        List<String> secondary = pickSecondaryMoods(content, mood);
        return new DiaryAnalysis(
                mood,
                intensity(content, mood),
                topics,
                secondary,
                summarize(content),
                feedbackFor(mood, topics));
    }

    private String pickMood(String content) {
        // 积极 / 高能量
        if (containsAny(content, "兴奋", "激动", "热血", "雀跃"))
            return "兴奋";
        if (containsAny(content, "期待", "盼望", "憧憬", "等待"))
            return "期待";
        if (containsAny(content, "自豪", "骄傲", "成就感", "成功"))
            return "自豪";
        if (containsAny(content, "开心", "高兴", "快乐", "喜悦", "愉快", "幸福", "安心"))
            return "喜悦";

        // 积极 / 低能量
        if (containsAny(content, "感恩", "感谢", "谢谢", "珍惜", "幸运"))
            return "感恩";
        if (containsAny(content, "满足", "充实", "圆满", "知足", "够了"))
            return "满足";
        if (containsAny(content, "轻松", "舒服", "自在", "惬意", "放松"))
            return "轻松";

        // 消极 / 高能量
        if (containsAny(content, "愤怒", "怒", "火大", "气死", "可恶", "生气"))
            return "愤怒";
        if (containsAny(content, "害怕", "恐惧", "吓", "恐慌", "怕"))
            return "害怕";
        if (containsAny(content, "焦虑", "担心", "紧张", "不安", "慌", "忐忑"))
            return "焦虑";
        if (containsAny(content, "烦", "烦躁", "不耐烦", "闹心"))
            return "烦躁";

        // 消极 / 低能量
        if (containsAny(content, "委屈", "冤枉", "不被理解", "凭什么"))
            return "委屈";
        if (containsAny(content, "难过", "伤心", "悲伤", "哭", "眼泪", "心碎"))
            return "难过";
        if (containsAny(content, "孤独", "孤单", "寂寞", "一个人", "没人陪"))
            return "孤独";
        if (containsAny(content, "迷茫", "不知道怎么办", "迷路", "方向", "困惑"))
            return "迷茫";
        if (containsAny(content, "内疚", "愧疚", "自责", "对不起", "后悔"))
            return "内疚";
        if (containsAny(content, "累", "疲惫", "困", "撑不住", "精疲力尽", "压力", "崩溃"))
            return "疲惫";

        return "平静";
    }

    private List<String> pickSecondaryMoods(String content, String primaryMood) {
        List<String> secondary = new ArrayList<>();
        // Only add secondary moods that differ from primary
        if (!primaryMood.equals("疲惫") && containsAny(content, "累", "困", "疲惫", "没力气"))
            secondary.add("疲惫");
        if (!primaryMood.equals("焦虑") && containsAny(content, "担心", "紧张", "不安", "慌"))
            secondary.add("焦虑");
        if (!primaryMood.equals("难过") && containsAny(content, "难过", "伤心", "想哭", "心酸"))
            secondary.add("难过");
        if (!primaryMood.equals("孤独") && containsAny(content, "孤独", "孤单", "寂寞"))
            secondary.add("孤独");
        if (!primaryMood.equals("迷茫") && containsAny(content, "迷茫", "不知道", "困惑"))
            secondary.add("迷茫");
        if (!primaryMood.equals("烦躁") && containsAny(content, "烦", "烦人", "闹心"))
            secondary.add("烦躁");
        if (!primaryMood.equals("委屈") && containsAny(content, "委屈", "凭什么"))
            secondary.add("委屈");
        return secondary;
    }

    private List<String> pickTopics(String content) {
        List<String> topics = new ArrayList<>();
        if (containsAny(content, "朋友", "同事", "家人", "关系", "聊天", "争吵", "误会"))
            topics.add("人际关系");
        if (containsAny(content, "工作", "加班", "任务", "项目", "考试", "学习", "上课"))
            topics.add("工作学习");
        if (containsAny(content, "睡", "失眠", "身体", "头痛", "胃", "运动"))
            topics.add("睡眠身体");
        if (containsAny(content, "自己", "未来", "目标", "坚持", "改变"))
            topics.add("自我成长");
        if (topics.isEmpty())
            topics.add("日常情绪");
        return topics;
    }

    private int intensity(String content, String mood) {
        // Base intensity determined by mood category
        int base = switch (mood) {
            // High-arousal moods tend to be more intense
            case "愤怒", "害怕", "恐慌" -> 3;
            case "焦虑", "兴奋", "委屈", "难过" -> 3;
            case "烦躁", "孤独", "迷茫" -> 2;
            case "疲惫", "内疚" -> 2;
            case "喜悦", "期待", "自豪" -> 2;
            // Low-arousal / calm moods
            case "感恩", "满足", "轻松" -> 1;
            case "平静" -> 1;
            default -> 2;
        };

        // Adverb/sentiment modifier (-2 to +2)
        int modifier = 0;
        if (containsAny(content, "崩溃", "绝望", "受不了", "要死了", "失控", "撑不住了"))
            modifier = 2;
        else if (containsAny(content, "极度", "非常", "强烈", "特别特别"))
            modifier = 1;
        else if (containsAny(content, "很", "特别", "一直", "真的", "明显"))
            modifier = 0; // base stays
        else if (containsAny(content, "有点", "稍微", "有些", "一点", "一点点"))
            modifier = -1;
        else if (containsAny(content, "略微", "淡淡的", "几乎没有", "不算"))
            modifier = -2;

        return Math.min(5, Math.max(1, base + modifier));
    }

    private String summarize(String content) {
        String compact = content.replaceAll("\\s+", " ");
        if (compact.length() <= 48)
            return compact;
        return compact.substring(0, 48) + "...";
    }

    private String feedbackFor(String mood, List<String> topics) {
        String topic = topics.get(0);
        return switch (mood) {
            // 积极 / 高能量
            case "喜悦" -> "这份喜悦值得被好好收藏，它是你生活里真实的光亮。";
            case "期待" -> "有所期待本身就是一种温柔的力量，让它慢慢滋养你。";
            case "兴奋" -> "这种热血沸腾的感觉很珍贵，记得享受当下的每一秒。";
            case "自豪" -> "你值得为自己骄傲，这份成就感是你一步步走出来的。";

            // 积极 / 低能量
            case "轻松" -> "这份轻松很珍贵，可以记住让你感觉被托住的细节。";
            case "平静" -> "这是一段关于" + topic + "的日常波动，慢慢记录会更看清自己的节奏。";
            case "感恩" -> "心怀感激的时候，世界也会变得柔软一些。记住这份温暖。";
            case "满足" -> "知足是一种安静的力量，今天的你已经足够好了。";

            // 消极 / 高能量
            case "烦躁" -> "烦躁可能是在提醒你边界被挤压了，给自己留一点缓冲。";
            case "愤怒" -> "愤怒背后往往藏着在意，先深呼吸，等情绪降温后再看看它想告诉你什么。";
            case "焦虑" -> "你正在承受一些不确定感，可以先把最小的一步从脑子里拿出来。";
            case "害怕" -> "害怕不是软弱，它是你在面对未知时本能的保护机制。慢慢来，不用逼自己。";

            // 消极 / 低能量
            case "疲惫" -> "今天已经消耗了你不少能量，休息不是退后，是在保护自己。";
            case "委屈" -> "这份委屈值得被看见，先不用急着替别人解释一切。";
            case "难过" -> "难过的时候不必急着好起来，允许自己在这个情绪里待一会儿。";
            case "孤独" -> "孤独感是人类共有的体验，你不是一个人在面对它。";
            case "迷茫" -> "看不清方向的时候，先走好眼前的一小步就够了。";
            case "内疚" -> "内疚说明你有一颗善良的心，但请记得对自己也温柔一点。";

            default -> "这是一段关于" + topic + "的日常波动，慢慢记录会更看清自己的节奏。";
        };
    }

    private boolean containsAny(String content, String... keywords) {
        for (String keyword : keywords) {
            if (content.contains(keyword))
                return true;
        }
        return false;
    }
}
