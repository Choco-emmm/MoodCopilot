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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import java.time.Duration;

@Service
public class AiAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisService.class);

    private final ChatClient analysisChatClient;
    private final ObjectMapper objectMapper;
    private final com.moodcopilot.config.AiPromptProperties aiPrompts;
    private final MemoryExtractionService memoryExtractionService;
    private final RagMemoryService ragMemoryService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    public AiAnalysisService(ChatClient analysisChatClient, ObjectMapper objectMapper, com.moodcopilot.config.AiPromptProperties aiPrompts,
                             @org.springframework.context.annotation.Lazy MemoryExtractionService memoryExtractionService,
                             @org.springframework.context.annotation.Lazy RagMemoryService ragMemoryService) {
        this.analysisChatClient = analysisChatClient;
        this.objectMapper = objectMapper;
        this.aiPrompts = aiPrompts;
        this.memoryExtractionService = memoryExtractionService;
        this.ragMemoryService = ragMemoryService;
    }

    public DiaryAnalysis analyze(Long userId, String content) {
        return analyze(userId, content, null, null);
    }

    @Async
    public void analyzeMusicAsync(String title, String artist, String lyrics, String cacheKey) {
        try {
            var result = analyzeMusicSync(title, artist, lyrics);
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                com.moodcopilot.entity.MusicMeta meta = objectMapper.readValue(cached, com.moodcopilot.entity.MusicMeta.class);
                meta.setMoodTags(result.getLeft());
                meta.setThemeSummary(result.getRight());
                redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(meta), Duration.ofDays(7));
            }
        } catch (Exception e) {
            log.error("AI music analysis failed for {} - {}: {}", artist, title, e.getMessage());
        }
    }

    /**
     * 同步分析歌曲氛围，返回 (moodTags, themeSummary)
     */
    public org.apache.commons.lang3.tuple.Pair<String, String> analyzeMusicSync(String title, String artist, String lyrics) {
        try {
            String prompt = String.format("总结歌曲《%s - %s》的语种、核心曲风、情感基调（3个词，逗号分隔）以及表达的核心主题（含语种信息，50字以内）。歌词如下：%s。请返回JSON格式：{\"moodTags\": \"...\", \"themeSummary\": \"...\"}", title, artist, lyrics);
            String json = analysisChatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            Map<String, String> result = objectMapper.readValue(JsonUtils.cleanJson(json), new TypeReference<Map<String, String>>() {});
            return org.apache.commons.lang3.tuple.Pair.of(
                    result.getOrDefault("moodTags", ""),
                    result.getOrDefault("themeSummary", ""));
        } catch (Exception e) {
            log.error("同步音乐分析失败 {} - {}: {}", artist, title, e.getMessage());
            return org.apache.commons.lang3.tuple.Pair.of("", "");
        }
    }

    public DiaryAnalysis analyze(Long userId, String content, com.moodcopilot.entity.MusicMeta musicMeta) {
        return analyze(userId, content, musicMeta, null);
    }

    public DiaryAnalysis analyze(Long userId, String content, com.moodcopilot.entity.MusicMeta musicMeta, String imageDescriptions) {
        StringBuilder sb = new StringBuilder();
        if (userId != null) {
            try {
                String coreMemory = memoryExtractionService.buildCoreUserMemoryPrompt(userId);
                if (coreMemory != null && !coreMemory.isBlank()) {
                    sb.append("[长期画像]\n").append(coreMemory).append("\n\n");
                }
                String ragContext = ragMemoryService.buildRagContext(userId, content, 5, RagMemoryService.SOURCE_DIARY);
                if (ragContext != null && !ragContext.isBlank()) {
                    sb.append("[近期相关记忆]\n").append(ragContext).append("\n");
                    sb.append("这是用户近期的相关历史日记，请结合前因后果进行分析。\n\n");
                }
            } catch (Exception e) {
                log.warn("Failed to retrieve memory contexts for user {}: {}", userId, e.getMessage());
            }
        }
        sb.append("[本次日记]\n").append(content);

        if (musicMeta != null) {
            sb.append("\n\n[音乐背景]\n");
            sb.append("歌曲：《").append(musicMeta.getTitle()).append("》，");
            sb.append("情感基调为 ").append(musicMeta.getMoodTags() != null ? musicMeta.getMoodTags() : "未知").append("，");
            sb.append("主要表达 ").append(musicMeta.getThemeSummary() != null ? musicMeta.getThemeSummary() : "未知").append("。\n");
            if (musicMeta.getUserLyric() != null && !musicMeta.getUserLyric().isBlank()) {
                sb.append("用户特别标注的歌词片段：").append(musicMeta.getUserLyric()).append("\n");
                sb.append("（用户主动选择了这段歌词，说明这段文字与用户当前心境有强烈共鸣，应将这段歌词视为用户自我表达的一部分，结合正文重点分析。）\n");
            }
            sb.append("（注意：歌曲整体的情感基调和主题仅供氛围参考。请主要基于用户自己写的正文进行情绪分析，如果正文很短或没有情绪表达，切勿过度放大音乐本身的极端情绪。）");
        }
        if (imageDescriptions != null && !imageDescriptions.isBlank()) {
            sb.append("\n\n[图片描述]\n").append(imageDescriptions).append("\n");
            sb.append("图片分为两类：1) 纯画面信息（场景、色调、氛围）作为情绪分析的辅助参考；2) 从图片中提取的文字内容（如聊天截图、手写笔记、文档等）视为用户日记正文的一部分，与正文同等权重分析，尤其在正文简短时，图片中的文字可能是用户真正的表达重点。");
        }

        String userPrompt = sb.toString();
        log.info("AI 日记分析上下文:\n{}", userPrompt);

        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String json = analysisChatClient.prompt()
                        .system(aiPrompts.getAnalysisSystemPrompt())
                        .user(userPrompt)
                        .call()
                        .content();
                return parseAiResponse(json);
            } catch (JsonProcessingException e) {
                if (attempt < maxRetries) {
                    log.warn("AI analysis JSON parsing failed, retrying (attempt {}/{}): {}", attempt, maxRetries, e.getMessage());
                } else {
                    log.error("AI analysis JSON parsing failed finally after {} attempts: {}", maxRetries, e.getMessage());
                    return keywordAnalyze(content);
                }
            } catch (Exception e) {
                log.warn("AI analysis failed, falling back to keyword analysis: {}", e.getMessage());
                return keywordAnalyze(content);
            }
        }
        return keywordAnalyze(content);
    }

    @SuppressWarnings("unchecked")
    private DiaryAnalysis parseAiResponse(String json) throws JsonProcessingException {
        Map<String, Object> map = objectMapper.readValue(JsonUtils.cleanJson(json), Map.class);
        String moodLabel = sanitizeString(map.get("moodLabel"), "复杂");
        int moodIntensity = 3;
        if (map.get("moodIntensity") instanceof Number n) {
            moodIntensity = n.intValue();
        } else if (map.get("moodIntensity") instanceof String s) {
            try { moodIntensity = Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        
        Integer valence = null;
        if (map.get("valence") instanceof Number n) {
            valence = n.intValue();
        }
        Integer arousal = null;
        if (map.get("arousal") instanceof Number n) {
            arousal = n.intValue();
        }

        List<String> topicLabels = sanitizeStringList(map.get("topicLabels"), List.of("日常情绪"));
        List<String> secondaryMoods = sanitizeStringList(map.get("secondaryMoods"), List.of());
        String summary = sanitizeString(map.get("summary"), "这是一篇关于心情记录的日记。");
        String feedback = sanitizeString(map.get("feedback"), "感谢你的记录，你的每一点感受都很重要。");
        List<String> safeSecondary = (secondaryMoods != null) ? secondaryMoods : List.of();

        // 若 AI 未返回 valence/arousal，根据标签估算
        if (valence == null) valence = estimateValence(moodLabel, moodIntensity);
        if (arousal == null) arousal = estimateArousal(moodLabel, moodIntensity);

        return new DiaryAnalysis(moodLabel, Math.min(5, Math.max(1, moodIntensity)),
                valence, arousal,
                topicLabels, safeSecondary, summary, feedback);
    }

    // ── Knowledge Graph Extraction ──

    public record KnowledgeTriple(String head, String relation, String tail, Integer tailPolarity) {}

    public List<KnowledgeTriple> extractKnowledgeGraph(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String json = analysisChatClient.prompt()
                        .system(aiPrompts.getGraphExtractionSystemPrompt())
                        .user(content)
                        .call()
                        .content();
                return objectMapper.readValue(JsonUtils.cleanJson(json), new TypeReference<List<KnowledgeTriple>>() {});
            } catch (Exception e) {
                if (attempt < maxRetries) {
                    log.warn("AI knowledge graph extraction failed, retrying (attempt {}/{}): {}", attempt, maxRetries, e.getMessage());
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    log.error("AI knowledge graph extraction failed finally after {} attempts: {}", maxRetries, e.getMessage());
                }
            }
        }
        return List.of();
    }

    public record DiaryEntryContext(String date, String content) {}

    // ── Weekly report ──

    public String generateWeeklySummary(List<DiaryEntryContext> diaryEntries, List<DiaryAnalysis> analyses,
            String memoryContext) {
        if (diaryEntries.isEmpty())
            return "本周还没有记录日记，去写一篇吧～";

        StringBuilder prompt = new StringBuilder();
        if (memoryContext != null && !memoryContext.isBlank()) {
            prompt.append("<user_profile>\n").append(memoryContext).append("\n</user_profile>\n\n");
        }
        prompt.append("本周日记摘要：\n");
        appendDiaryEntries(prompt, diaryEntries, analyses);
        prompt.append("\n").append(buildQuadrantHint(analyses));

        try {
            return analysisChatClient.prompt()
                    .system(aiPrompts.getWeeklySystemPrompt())
                    .user(prompt.toString())
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("AI weekly summary failed, falling back: {}", e.getMessage());
            return fallbackWeeklySummary(diaryEntries.size(), analyses);
        }
    }

    public ReportGuidance generateWeeklyGuidance(List<DiaryEntryContext> diaryEntries, List<DiaryAnalysis> analyses) {
        return generateReportGuidance("本周", diaryEntries, analyses);
    }

    public ReportGuidance generateCustomGuidance(String period, List<DiaryEntryContext> diaryEntries,
            List<DiaryAnalysis> analyses) {
        return generateReportGuidance(period, diaryEntries, analyses);
    }

    // ── Custom summary (date-range agnostic) ──

    public String generateCustomSummary(List<DiaryEntryContext> diaryEntries, List<DiaryAnalysis> analyses) {
        if (diaryEntries.isEmpty())
            return "该时段还没有记录日记，去写一篇吧～";

        StringBuilder prompt = new StringBuilder("自选时段日记摘要：\n");
        appendDiaryEntries(prompt, diaryEntries, analyses);

        try {
            return analysisChatClient.prompt()
                    .system(aiPrompts.getCustomSummarySystemPrompt())
                    .user(prompt.toString())
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("AI custom summary failed, falling back: {}", e.getMessage());
            return fallbackWeeklySummary(diaryEntries.size(), analyses);
        }
    }

    // ── Monthly report ──

    public String generateMonthlySummary(List<DiaryEntryContext> diaryEntries, List<DiaryAnalysis> analyses,
            String memoryContext) {
        if (diaryEntries.isEmpty())
            return "本月还没有记录日记，去写一篇吧～";

        List<DiaryEntryContext> filteredEntries = new ArrayList<>(diaryEntries);
        List<DiaryAnalysis> filteredAnalyses = new ArrayList<>(analyses);
        
        boolean truncated = false;
        if (diaryEntries.size() > 20) {
            truncated = true;
            record Paired(DiaryEntryContext entry, DiaryAnalysis analysis, int intensity, int originalIndex) {}
            List<Paired> pairs = new ArrayList<>();
            for (int i = 0; i < diaryEntries.size(); i++) {
                DiaryAnalysis a = i < analyses.size() ? analyses.get(i) : null;
                int intensity = (a != null) ? a.moodIntensity() : 3;
                pairs.add(new Paired(diaryEntries.get(i), a, intensity, i));
            }
            
            pairs.sort((p1, p2) -> Integer.compare(p2.intensity(), p1.intensity()));
            List<Paired> top20 = pairs.subList(0, 20);
            top20.sort(java.util.Comparator.comparingInt(Paired::originalIndex));
            
            filteredEntries.clear();
            filteredAnalyses.clear();
            for (Paired p : top20) {
                filteredEntries.add(p.entry());
                filteredAnalyses.add(p.analysis());
            }
        }

        StringBuilder prompt = new StringBuilder();
        if (memoryContext != null && !memoryContext.isBlank()) {
            prompt.append("<user_profile>\n").append(memoryContext).append("\n</user_profile>\n\n");
        }
        
        if (truncated) {
            prompt.append(String.format("本月共记录了 %d 篇日记，此处为你提取了情绪波动最强烈的 20 篇供趋势分析：\n", diaryEntries.size()));
        } else {
            prompt.append("本月日记摘要：\n");
        }
        
        appendDiaryEntries(prompt, filteredEntries, filteredAnalyses);
        prompt.append("\n").append(buildQuadrantHint(analyses));

        try {
            return analysisChatClient.prompt()
                    .system(aiPrompts.getMonthlySystemPrompt())
                    .user(prompt.toString())
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("AI monthly summary failed, falling back: {}", e.getMessage());
            return fallbackMonthlySummary(diaryEntries.size(), analyses);
        }
    }

    public ReportGuidance generateMonthlyGuidance(List<DiaryEntryContext> diaryEntries, List<DiaryAnalysis> analyses) {
        return generateReportGuidance("本月", diaryEntries, analyses);
    }

    @SuppressWarnings("unchecked")
    private ReportGuidance generateReportGuidance(String period, List<DiaryEntryContext> diaryEntries,
            List<DiaryAnalysis> analyses) {
        if (diaryEntries.isEmpty()) {
            return new ReportGuidance(List.of(), List.of(), "等你多记录几天，我们再一起看看变化。");
        }
        StringBuilder prompt = new StringBuilder(period).append("日记模式：\n");
        for (int i = 0; i < diaryEntries.size(); i++) {
            DiaryAnalysis analysis = i < analyses.size() ? analyses.get(i) : null;
            prompt.append("- [").append(diaryEntries.get(i).date()).append("] ");
            if (analysis != null) {
                prompt.append("情绪：").append(analysis.moodLabel());
                if (analysis.hasSecondaryMoods()) {
                    prompt.append("（同时：").append(String.join("、", analysis.secondaryMoods())).append("）");
                }
                prompt.append("，强度：").append(analysis.moodIntensity());
                if (analysis.valence() != null) prompt.append("，正负向：").append(analysis.valence());
                if (analysis.arousal() != null) prompt.append("，唤醒度：").append(analysis.arousal());
                prompt.append("，主题：").append(String.join("、", analysis.topicLabels()))
                        .append("，摘要：").append(analysis.summary());
            } else {
                String content = diaryEntries.get(i).content();
                prompt.append(content.length() > 80 ? content.substring(0, 80) + "..." : content);
            }
            prompt.append("\n");
        }
        try {
            String json = analysisChatClient.prompt()
                    .system(aiPrompts.getReportGuidanceSystemPrompt())
                    .user(prompt.toString())
                    .call()
                    .content();
            Map<String, Object> map = objectMapper.readValue(JsonUtils.cleanJson(json), Map.class);
            return new ReportGuidance(
                    sanitizeStringList(map.get("insights"), fallbackInsights(analyses)),
                    sanitizeStringList(map.get("suggestions"), fallbackSuggestions(analyses)),
                    sanitizeString(map.get("followUpPrompt"), fallbackFollowUp(analyses)));
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
                "今晚给自己留一点安静空间，把注意力放回当下的感受。",
                "下次记录时，可以多写一句「这件事真正影响我的地方是？」");
    }

    private String fallbackFollowUp(List<DiaryAnalysis> analyses) {
        return "我想继续聊聊最近的「" + topMood(analyses) + "」从哪里来。";
    }

    private String topMood(List<DiaryAnalysis> analyses) {
        Map<String, Double> weighted = new HashMap<>();
        for (DiaryAnalysis a : analyses) {
            if (a == null || a.moodLabel() == null)
                continue;
            double intensity = a.moodIntensity();
            weighted.merge(a.moodLabel(), 1.0 * intensity, Double::sum);
            if (a.secondaryMoods() != null) {
                for (String s : a.secondaryMoods()) {
                    weighted.merge(s, 0.5 * intensity, Double::sum);
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

    private List<String> sanitizeStringList(Object value, List<String> fallback) {
        if (value instanceof List<?> list) {
            List<String> result = list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .limit(3)
                    .toList();
            return result.isEmpty() ? fallback : result;
        } else if (value instanceof String str) {
            if (str.isBlank()) return fallback;
            return List.of(str.trim());
        }
        return fallback;
    }

    private String sanitizeString(Object value, String fallback) {
        if (value instanceof String str) {
            if (str.isBlank()) return fallback;
            return str.trim();
        }
        return fallback;
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

    public String generateCoaching(List<DiaryEntryContext> diaryEntries, List<DiaryAnalysis> analyses) {
        if (diaryEntries.isEmpty())
            return "还没有足够的日记数据，多记录几天后我会为你生成陪跑建议。";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < diaryEntries.size(); i++) {
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
                    .system(aiPrompts.getCoachingSystemPrompt())
                    .user(sb.toString())
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("AI coaching failed: {}", e.getMessage());
            String topMood = topMood(analyses);
            return "你最近的情绪以「" + topMood + "」为主。先别急着评判自己，试着把此刻最真实的感受写成一句话。";
        }
    }

    // ── User chat context ──

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
                    .system(aiPrompts.getUserContextSystemPrompt())
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

    public List<String> generateEncouragements(String diaryContent) {
        try {
            String response = analysisChatClient.prompt()
                    .system(aiPrompts.getEncouragementSystemPrompt())
                    .user(diaryContent)
                    .call()
                    .content();
            return objectMapper.readValue(JsonUtils.cleanJson(response), new TypeReference<List<String>>() {
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

    private void appendDiaryEntries(StringBuilder prompt, List<DiaryEntryContext> diaryEntries, List<DiaryAnalysis> analyses) {
        for (int i = 0; i < diaryEntries.size(); i++) {
            DiaryEntryContext entry = diaryEntries.get(i);
            DiaryAnalysis a = i < analyses.size() ? analyses.get(i) : null;
            prompt.append("- [").append(entry.date()).append("] ");
            if (a != null) {
                prompt.append("情绪：").append(a.moodLabel());
                if (a.hasSecondaryMoods()) {
                    prompt.append("（同时感受到：").append(String.join("、", a.secondaryMoods())).append("）");
                }
                prompt.append("，强度：").append(a.moodIntensity());
                if (a.valence() != null) prompt.append("，正负向：").append(a.valence());
                if (a.arousal() != null) prompt.append("，唤醒度：").append(a.arousal());
                prompt.append("，主题：").append(String.join("、", a.topicLabels()))
                        .append("，摘要：").append(a.summary());
            } else {
                String content = entry.content();
                prompt.append(content.length() > 60 ? content.substring(0, 60) + "..." : content);
            }
            prompt.append("\n");
        }
    }

    private String buildQuadrantHint(List<DiaryAnalysis> analyses) {
        if (analyses == null || analyses.isEmpty()) {
            return "情绪四象限分布：暂无数据";
        }

        int posHigh = 0;
        int posLow = 0;
        int negHigh = 0;
        int negLow = 0;

        for (DiaryAnalysis analysis : analyses) {
            if (analysis == null || analysis.moodLabel() == null) {
                continue;
            }
            boolean positive;
            boolean highEnergy;
            
            if (analysis.valence() != null && analysis.arousal() != null) {
                positive = analysis.valence() > 0;
                highEnergy = analysis.arousal() > 0;
            } else {
                String mood = analysis.moodLabel();
                positive = isPositiveMood(mood);
                highEnergy = isHighEnergyMood(mood);
            }

            if (positive && highEnergy)
                posHigh++;
            if (positive && !highEnergy)
                posLow++;
            if (!positive && highEnergy)
                negHigh++;
            if (!positive && !highEnergy)
                negLow++;
        }

        int total = posHigh + posLow + negHigh + negLow;
        if (total == 0) {
            return "情绪四象限分布：暂无数据";
        }

        int positiveRatio = (int) Math.round(((posHigh + posLow) * 100.0) / total);
        int highEnergyRatio = (int) Math.round(((posHigh + negHigh) * 100.0) / total);

        return "情绪四象限分布："
                + "正向高能量=" + posHigh + "，"
                + "正向低能量=" + posLow + "，"
                + "负向高能量=" + negHigh + "，"
                + "负向低能量=" + negLow + "。"
                + "正向占比=" + positiveRatio + "%；高能量占比=" + highEnergyRatio + "%";
    }

    private boolean isPositiveMood(String moodLabel) {
        return List.of("喜悦", "期待", "兴奋", "自豪", "轻松", "平静", "感恩", "满足").contains(moodLabel);
    }

    private boolean isHighEnergyMood(String moodLabel) {
        return List.of("喜悦", "期待", "兴奋", "自豪", "烦躁", "愤怒", "焦虑", "害怕").contains(moodLabel);
    }

    // ══════════════════════════════════════════════
    // Keyword-based fallback (expanded taxonomy)
    // ══════════════════════════════════════════════

    private DiaryAnalysis keywordAnalyze(String content) {
        String mood = pickMood(content);
        List<String> topics = pickTopics(content);
        List<String> secondary = pickSecondaryMoods(content, mood);
        int intsy = intensity(content, mood);
        return new DiaryAnalysis(
                mood,
                intsy,
                estimateValence(mood, intsy),
                estimateArousal(mood, intsy),
                topics,
                secondary,
                summarize(content),
                feedbackFor(mood, topics));
    }

    public static Integer estimateValence(String moodLabel, int intensity) {
        if ("平静".equals(moodLabel)) return 10;
        int base = List.of("喜悦", "期待", "兴奋", "自豪", "轻松", "平静", "感恩", "满足").contains(moodLabel) ? 60 : -60;
        return base + (base > 0 ? (intensity - 3) * 15 : -(intensity - 3) * 15);
    }

    public static Integer estimateArousal(String moodLabel, int intensity) {
        if ("平静".equals(moodLabel)) return -10;
        int base = List.of("喜悦", "期待", "兴奋", "自豪", "烦躁", "愤怒", "焦虑", "害怕").contains(moodLabel) ? 60 : -60;
        return base + (base > 0 ? (intensity - 3) * 15 : -(intensity - 3) * 15);
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

    /**
     * 将用户口语化输入改写为日记风格的陈述句，用于向量语义检索（HyDE）。
     * @param query 用户当前消息
     * @param memoryContext 长期画像背景（可为空）
     * @param chatHistoryContext 最近对话历史（可为空，第一轮传 ""）
     */
    public String rewriteQueryForSearch(String query, String memoryContext, String chatHistoryContext) {
        try {
            StringBuilder prompt = new StringBuilder(aiPrompts.getQueryRewritePrompt());
            if (chatHistoryContext != null && !chatHistoryContext.isBlank()) {
                prompt.append("\n<chat_history>\n").append(chatHistoryContext).append("</chat_history>\n");
            }
            if (memoryContext != null && !memoryContext.isBlank()) {
                prompt.append("\n用户长期画像：").append(memoryContext).append("\n");
            }
            prompt.append("\n用户输入：").append(query);
            String result = analysisChatClient.prompt()
                    .user(prompt.toString())
                    .call()
                    .content();
            if (result != null && !result.isBlank()) {
                return result.trim();
            }
        } catch (Exception e) {
            log.debug("Query 重写失败: {}", e.getMessage());
        }
        return query; // 降级：返回原始输入
    }
}
