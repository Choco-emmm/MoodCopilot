package com.moodcopilot.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moodcopilot.entity.NotificationEntity;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.mapper.NotificationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationMapper notificationMapper;

    public NotificationService(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    public Page<NotificationEntity> getNotifications(Long recipientUserId, int page, int size) {
        int cappedPage = Math.max(1, page);
        int cappedSize = Math.min(50, Math.max(1, size));
        Page<NotificationEntity> p = Page.of(cappedPage, cappedSize);
        return notificationMapper.selectPage(p,
                new LambdaQueryWrapper<NotificationEntity>()
                        .eq(NotificationEntity::getRecipientUserId, recipientUserId)
                        .orderByDesc(NotificationEntity::getCreatedAt));
    }

    public long getUnreadCount(Long recipientUserId) {
        return notificationMapper.selectCount(
                new LambdaQueryWrapper<NotificationEntity>()
                        .eq(NotificationEntity::getRecipientUserId, recipientUserId)
                        .eq(NotificationEntity::getIsRead, false));
    }

    @Transactional
    public void markAsRead(Long notificationId, Long recipientUserId) {
        NotificationEntity notification = notificationMapper.selectById(notificationId);
        if (notification != null && notification.getRecipientUserId().equals(recipientUserId)) {
            notification.setIsRead(true);
            notification.setReadAt(LocalDateTime.now());
            notificationMapper.updateById(notification);
        }
    }

    public void notifyComment(UserEntity actor, Long diaryId, Long recipientUserId, Long commentId, String snippet) {
        try {
            NotificationEntity n = new NotificationEntity();
            n.setRecipientUserId(recipientUserId);
            n.setActorUserId(actor.getId());
            n.setDiaryId(diaryId);
            n.setCommentId(commentId);
            n.setType("COMMENT");
            n.setMessage(actor.getDisplayName() + " 评论了你的日记：" + snippet);
            n.setIsRead(false);
            n.setCreatedAt(LocalDateTime.now());
            notificationMapper.insert(n);
        } catch (Exception e) {
            log.warn("Failed to create comment notification", e);
        }
    }

    public void notifyResonance(UserEntity actor, Long diaryId, Long recipientUserId) {
        try {
            NotificationEntity n = new NotificationEntity();
            n.setRecipientUserId(recipientUserId);
            n.setActorUserId(actor.getId());
            n.setDiaryId(diaryId);
            n.setType("RESONANCE");
            n.setMessage(actor.getDisplayName() + " 对你的日记产生了共鸣");
            n.setIsRead(false);
            n.setCreatedAt(LocalDateTime.now());
            notificationMapper.insert(n);
        } catch (Exception e) {
            log.warn("Failed to create resonance notification", e);
        }
    }
}
