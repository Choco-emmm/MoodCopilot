package com.moodcopilot.diary;

public record CreateCommentRequest(String content, Long parentCommentId) {
}
