package com.moodcopilot.announcement;

import com.moodcopilot.entity.SystemAnnouncementEntity;

import java.time.LocalDateTime;

public record AnnouncementView(
        Long id,
        Long version,
        String title,
        String content,
        LocalDateTime publishedAt,
        Long publishedByUserId,
        String publishedByDisplayName) {

    static AnnouncementView from(SystemAnnouncementEntity entity, String displayName) {
        return new AnnouncementView(
                entity.getId(),
                entity.getVersion(),
                entity.getTitle(),
                entity.getContent(),
                entity.getPublishedAt(),
                entity.getPublishedByUserId(),
                displayName);
    }
}
