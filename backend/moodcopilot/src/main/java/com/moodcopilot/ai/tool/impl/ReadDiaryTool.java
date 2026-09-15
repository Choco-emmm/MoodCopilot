package com.moodcopilot.ai.tool.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodcopilot.ai.tool.ChatTool;
import com.moodcopilot.ai.tool.ToolExecutionContext;
import com.moodcopilot.common.TextSnippetUtil;
import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.mapper.DiaryMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按 id 读一篇日记的**正文**。
 * <p>
 * 搜索只能给出索引条目（AI 摘要或短节选）—— 那不足以回答「第三条面包几分」这类问题。
 * 有了这个工具，搜索就不必每次都塞正文进上下文，模型也能按需展开它真正要看的那些。
 * <p>
 * 返回的是纯文本：剥掉富文本标签但**保留段落结构**，条目与列表不会黏成一坨。
 */
public class ReadDiaryTool extends ChatTool<ReadDiaryTool.ReadDiaryRequest> {

    public static final String NAME = "readDiaryFunction";

    private static final int MAX_CHARS = 4000;

    private final DiaryMapper diaryMapper;

    public ReadDiaryTool(DiaryMapper diaryMapper) {
        this.diaryMapper = diaryMapper;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "按 id 读取一篇日记的完整正文（纯文本）。"
                + "搜索返回的只是摘要或节选，当它不足以回答用户的具体问题（具体数字、清单、原话细节）时，必须用搜索结果里的 diaryId 调用本工具取全文，不要凭摘要猜。"
                + "只能读当前用户自己的日记。";
    }

    @Override
    public Class<ReadDiaryRequest> requestType() {
        return ReadDiaryRequest.class;
    }

    @Override
    public LinkedHashMap<String, Object> properties() {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put("diaryId", Map.of("type", "integer", "description", "日记 id，来自搜索结果"));
        return props;
    }

    @Override
    public List<String> required() {
        return List.of("diaryId");
    }

    @Override
    public Object execute(ReadDiaryRequest request, ToolExecutionContext context) {
        long userId = context.userId();
        if (request.diaryId() == null) {
            return new ReadDiaryResult(false, null, null, null, "缺少 diaryId，无法读取日记");
        }
        // 归属校验写在查询条件里：不是本人的日记查不出来，与不存在同一种结果
        DiaryEntity diary = diaryMapper.selectOne(new LambdaQueryWrapper<DiaryEntity>()
                .eq(DiaryEntity::getId, request.diaryId())
                .eq(DiaryEntity::getAuthorUserId, userId)
                .eq(DiaryEntity::getIsDeleted, false));
        if (diary == null) {
            return new ReadDiaryResult(false, null, null, null, "没有找到这篇日记，或者它不属于当前用户");
        }

        String text = TextSnippetUtil.toPlainText(diary.getContent());
        if (text.isBlank()) {
            return new ReadDiaryResult(false, diary.getId(), null, null, "这篇日记没有正文内容");
        }

        String note = null;
        if (text.codePointCount(0, text.length()) > MAX_CHARS) {
            text = text.substring(0, text.offsetByCodePoints(0, MAX_CHARS)) + "…";
            note = "正文超过 " + MAX_CHARS + " 字，已截断";
        }
        if (diary.getImages() != null && !diary.getImages().isEmpty()) {
            String imageNote = "这篇还带了 " + diary.getImages().size() + " 张图片";
            note = note == null ? imageNote : note + "；" + imageNote;
        }

        return new ReadDiaryResult(true, diary.getId(),
                diary.getCreatedAt() == null ? null : diary.getCreatedAt().toString(), text, note);
    }

    public record ReadDiaryRequest(Long diaryId) {
    }

    public record ReadDiaryResult(boolean success, Long id, String date, String content, String note) {
    }
}
