package com.moodcopilot.ai;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MemoryEvidenceSourceTest {
    @Test void legacyAssistantQuotesStayInHistoryButNotInUserFactEvidence() {
        String message = "[用户引用了之前的发言：\n\"你喜欢咖啡\"]\n\n用户的回复是：\n不对，我喜欢茶";
        var turn = new ChatTurn(List.of(), message, 32L, List.of());
        assertEquals("不对，我喜欢茶", turn.userMessage());
        assertTrue(message.contains("你喜欢咖啡"));
        assertFalse(MemoryWritePolicy.evidence("你喜欢咖啡", turn.userMessage()));
        assertTrue(MemoryWritePolicy.evidence("我喜欢茶", turn.userMessage()));
    }

    @Test void onlyOwnedUserAuthoredReferenceTextCanBecomeEvidence() {
        var diary = reference("USER_DIARY", "user", 7L, "用户日记");
        var assistant = reference("ASSISTANT_MESSAGE", "assistant", 7L, "助手猜测");
        var caption = reference("SYSTEM_IMAGE_CAPTION", "system", 7L, "图片描述");
        var foreign = reference("USER_DIARY", "user", 8L, "别人的日记");
        assertEquals(List.of("用户日记"), MemoryEvidenceSource.references(List.of(diary, assistant, caption, foreign), 7L));
    }

    UserReference reference(String type, String author, long userId, String content) {
        return new UserReference(content, new ContextSource(type, "1", author, "original", null, null,
                ContextSource.TrustLevel.AUTHORITATIVE, userId), ReferencePurpose.DISCUSS, 1D, 60, false);
    }
}
