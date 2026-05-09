package com.moodcopilot.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.diary.DiaryAnalysis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AiAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisService.class);

    private static final String SYSTEM_PROMPT = """
            You are a compassionate emotion analysis assistant. Analyze the following diary entry and return ONLY valid JSON (no markdown, no explanation). The JSON must have these exact fields:
            - moodLabel: one of [焦虑, 委屈, 烦躁, 疲惫, 轻松, 平静]
            - moodIntensity: integer 1-5
            - topicLabels: array of strings from [人际关系, 工作学习, 睡眠身体, 自我成长, 日常情绪]
            - summary: brief Chinese summary, max 48 characters
            - feedback: gentle, compassionate Chinese feedback, max 200 characters""";

    private static final String WEEKLY_SYSTEM_PROMPT = """
            You are a compassionate weekly reflection assistant. Below is a list of diary entries from the past week, each with its mood label, topic, and summary. Write a warm, gentle Chinese reflection (150-300 characters) that:
            1. Acknowledges the emotional journey of the week
            2. Notices patterns or shifts in mood and themes
            3. Offers gentle encouragement without being preachy
            Return ONLY the Chinese text, no markdown, no JSON, no explanation.""";

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
        String summary = (String) map.get("summary");
        String feedback = (String) map.get("feedback");
        return new DiaryAnalysis(moodLabel, Math.min(5, Math.max(1, moodIntensity)),
                topicLabels, summary, feedback);
    }

    // ── Weekly report ──

    public String generateWeeklySummary(List<String> diaryContents, List<DiaryAnalysis> analyses) {
        if (diaryContents.isEmpty()) return "本周还没有记录日记，去写一篇吧～";

        StringBuilder prompt = new StringBuilder("本周日记摘要：\n");
        for (int i = 0; i < diaryContents.size(); i++) {
            DiaryAnalysis a = i < analyses.size() ? analyses.get(i) : null;
            prompt.append("- ");
            if (a != null) {
                prompt.append("情绪：").append(a.moodLabel())
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

    private String fallbackWeeklySummary(int count, List<DiaryAnalysis> analyses) {
        if (count == 0) return "本周还没有记录日记，去写一篇吧～";

        var moodCounts = analyses.stream()
                .collect(Collectors.groupingBy(
                        DiaryAnalysis::moodLabel,
                        Collectors.counting()
                ));
        String topMood = moodCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("—");

        return String.format("本周共记录了 %d 篇日记，主要情绪为「%s」。继续记录，你会慢慢看清自己的节奏。", count, topMood);
    }

    // ── Keyword-based fallback ──

    private DiaryAnalysis keywordAnalyze(String content) {
        String mood = pickMood(content);
        List<String> topics = pickTopics(content);
        return new DiaryAnalysis(
                mood,
                intensity(content, mood),
                topics,
                summarize(content),
                feedbackFor(mood, topics)
        );
    }

    private String pickMood(String content) {
        if (containsAny(content, "焦虑", "担心", "紧张", "害怕", "慌")) return "焦虑";
        if (containsAny(content, "委屈", "难过", "想哭", "失落", "孤单")) return "委屈";
        if (containsAny(content, "生气", "烦", "愤怒", "讨厌")) return "烦躁";
        if (containsAny(content, "累", "疲惫", "困", "撑", "压力", "崩溃")) return "疲惫";
        if (containsAny(content, "开心", "高兴", "舒服", "期待", "安心")) return "轻松";
        return "平静";
    }

    private List<String> pickTopics(String content) {
        List<String> topics = new ArrayList<>();
        if (containsAny(content, "朋友", "同事", "家人", "关系", "聊天", "争吵", "误会")) topics.add("人际关系");
        if (containsAny(content, "工作", "加班", "任务", "项目", "考试", "学习", "上课")) topics.add("工作学习");
        if (containsAny(content, "睡", "失眠", "身体", "头痛", "胃", "运动")) topics.add("睡眠身体");
        if (containsAny(content, "自己", "未来", "目标", "坚持", "改变")) topics.add("自我成长");
        if (topics.isEmpty()) topics.add("日常情绪");
        return topics;
    }

    private int intensity(String content, String mood) {
        int base = switch (mood) {
            case "焦虑", "委屈", "烦躁" -> 3;
            case "疲惫" -> 2;
            default -> 1;
        };
        int extra = containsAny(content, "很", "特别", "一直", "真的", "崩溃") ? 1 : 0;
        return Math.min(5, base + extra);
    }

    private String summarize(String content) {
        String compact = content.replaceAll("\\s+", " ");
        if (compact.length() <= 48) return compact;
        return compact.substring(0, 48) + "...";
    }

    private String feedbackFor(String mood, List<String> topics) {
        String topic = topics.get(0);
        return switch (mood) {
            case "焦虑" -> "你正在承受一些不确定感，可以先把最小的一步从脑子里拿出来。";
            case "委屈" -> "这份委屈值得被看见，先不用急着替别人解释一切。";
            case "烦躁" -> "烦躁可能是在提醒你边界被挤压了，给自己留一点缓冲。";
            case "疲惫" -> "今天已经消耗了你不少能量，休息不是退后，是在保护自己。";
            case "轻松" -> "这份轻松很珍贵，可以记住让你感觉被托住的细节。";
            default -> "这是一段关于" + topic + "的日常波动，慢慢记录会更看清自己的节奏。";
        };
    }

    private boolean containsAny(String content, String... keywords) {
        for (String keyword : keywords) {
            if (content.contains(keyword)) return true;
        }
        return false;
    }
}
