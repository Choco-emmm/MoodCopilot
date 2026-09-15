package com.moodcopilot.ai;

import java.util.List;

/**
 * 用户这一轮带给模型的东西，在 {@link ChatAgentLoop#run} 里整包传给工具层。
 * <p>
 * {@code userMessage} 与 {@code userReferences} 是给记忆类工具用的：批准一条记忆时，
 * 落库的「证据」必须是用户自己的话，不能是模型转述的摘要 ——
 * 与 {@code MemoryExtractionService#buildChatExtractionEvidence} 同一套取材口径
 * （用户消息 + 用户引用，**刻意排除助手回复**）。
 */
public record ChatTurn(List<String> imageUrls, String userMessage, Long conversationId,
        List<String> userReferences) {

    public ChatTurn {
        imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
        userMessage = userMessage == null ? "" : userMessage;
        userReferences = userReferences == null ? List.of() : List.copyOf(userReferences);
    }

    /** 只要附件、没有对话上下文时用（非聊天路径与单测）。 */
    public static ChatTurn of(List<String> imageUrls) {
        return new ChatTurn(imageUrls, "", null, List.of());
    }
}
