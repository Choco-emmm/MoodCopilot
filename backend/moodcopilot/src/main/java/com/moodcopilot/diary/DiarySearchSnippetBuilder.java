package com.moodcopilot.diary;

import com.moodcopilot.common.TextSnippetUtil;
import com.moodcopilot.entity.DiaryEntity;

/**
 * 搜索命中的日记 → 交给模型看的**索引条目**。
 * <p>
 * 两条搜索路径（{@code RagMemoryService.searchForTool} 的向量检索，与
 * {@code DiaryService.searchOwnDiarySummaries} 的关键词/日期兜底）必须共用这一份。
 * 它们此前各写一遍，一条给 3000 字正文原文、另一条给 500 字压平节选，
 * 质量差一个数量级 —— 那正是「日记被截断」的根源。
 * <p>
 * 条目只说明「这篇讲了什么」，正文一律由 readDiaryFunction 按需取。
 */
public final class DiarySearchSnippetBuilder {

    /** 没有 AI 摘要时，退回多长的正文节选。够模型判断要不要展开，又不至于占满上下文。 */
    private static final int EXCERPT_CHARS = 120;

    private DiarySearchSnippetBuilder() {
    }

    /**
     * @param analysisSummary  该篇的 AI 摘要（≤48 字）；没有做过分析时传 null
     * @param matchedImageDesc 语义检索命中的图片描述；关键词兜底路径传 null
     */
    public static String build(DiaryEntity diary, String analysisSummary, String matchedImageDesc) {
        if (diary == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();

        if (diary.getMusicMeta() != null && diary.getMusicMeta().getTitle() != null
                && !diary.getMusicMeta().getTitle().isBlank()) {
            sb.append("[分享音乐：").append(diary.getMusicMeta().getTitle());
            if (diary.getMusicMeta().getArtist() != null && !diary.getMusicMeta().getArtist().isBlank()) {
                sb.append(" - ").append(diary.getMusicMeta().getArtist());
            }
            sb.append("] ");
        }

        if (matchedImageDesc != null && !matchedImageDesc.isBlank()) {
            String desc = matchedImageDesc.startsWith("【图片描述】")
                    ? matchedImageDesc.substring("【图片描述】".length())
                    : matchedImageDesc;
            sb.append("[图片描述：").append(desc).append("] ");
        } else if (diary.getImages() != null && !diary.getImages().isEmpty()) {
            sb.append("[分享图片] ");
        }

        String gist = analysisSummary != null && !analysisSummary.isBlank()
                ? analysisSummary.trim()
                : excerpt(diary.getContent());
        if (!gist.isEmpty()) {
            sb.append(gist);
        }
        return sb.toString().trim();
    }

    /** 没有 AI 摘要时的兜底：正文开头的短节选，保留段落。 */
    private static String excerpt(String content) {
        String plain = TextSnippetUtil.toPlainText(content);
        if (plain.isEmpty()) {
            return "";
        }
        return plain.codePointCount(0, plain.length()) <= EXCERPT_CHARS
                ? plain
                : plain.substring(0, plain.offsetByCodePoints(0, EXCERPT_CHARS)) + "…";
    }
}
