package com.moodcopilot.diary;

import java.time.LocalDate;
import java.util.List;

/**
 * 日记摘要检索结果。
 * <p>
 * {@code total} 是**匹配总数**，不是 {@code diaries} 的条数。两者不等时说明被截断了，
 * 调用方（尤其是模型）必须据此缩小时间范围重查，不能把这一页当成全部。
 * {@code note} 里已经写清了这一点。
 */
public record DiarySearchResult(
        String keyword,
        LocalDate startDate,
        LocalDate endDate,
        int total,
        List<DiarySummary> diaries,
        String note
) {

    public record DiarySummary(
            Long id,
            String date,
            String snippet
    ) {
    }
}
