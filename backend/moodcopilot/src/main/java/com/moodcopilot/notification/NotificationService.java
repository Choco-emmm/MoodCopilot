package com.moodcopilot.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
    private final NotificationWebSocketHandler notificationWebSocketHandler;

    public NotificationService(NotificationMapper notificationMapper,
            NotificationWebSocketHandler notificationWebSocketHandler) {
        this.notificationMapper = notificationMapper;
        this.notificationWebSocketHandler = notificationWebSocketHandler;
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
        notificationMapper.update(
                null,
                new LambdaUpdateWrapper<NotificationEntity>()
                        .eq(NotificationEntity::getId, notificationId)
                        .eq(NotificationEntity::getRecipientUserId, recipientUserId)
                        .eq(NotificationEntity::getIsRead, false)
                        .set(NotificationEntity::getIsRead, true)
                        .set(NotificationEntity::getReadAt, LocalDateTime.now()));
    }

    @Transactional
    public void markAllAsRead(Long recipientUserId) {
        notificationMapper.update(
                null,
                new LambdaUpdateWrapper<NotificationEntity>()
                        .eq(NotificationEntity::getRecipientUserId, recipientUserId)
                        .eq(NotificationEntity::getIsRead, false)
                        .set(NotificationEntity::getIsRead, true)
                        .set(NotificationEntity::getReadAt, LocalDateTime.now()));
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
            n.setIsMarkdown(false);
            n.setIsRead(false);
            n.setCreatedAt(LocalDateTime.now());
            notificationMapper.insert(n);
            notificationWebSocketHandler.pushNotification(recipientUserId, n);
        } catch (Exception e) {
            log.warn("Failed to create comment notification", e);
        }
    }

    public void notifyCommentReply(UserEntity actor, Long diaryId, Long recipientUserId, Long commentId, String snippet) {
        try {
            NotificationEntity n = new NotificationEntity();
            n.setRecipientUserId(recipientUserId);
            n.setActorUserId(actor.getId());
            n.setDiaryId(diaryId);
            n.setCommentId(commentId);
            n.setType("COMMENT");
            n.setMessage(actor.getDisplayName() + " 回复了你的评论：" + snippet);
            n.setIsMarkdown(false);
            n.setIsRead(false);
            n.setCreatedAt(LocalDateTime.now());
            notificationMapper.insert(n);
            notificationWebSocketHandler.pushNotification(recipientUserId, n);
        } catch (Exception e) {
            log.warn("Failed to create comment reply notification", e);
        }
    }

    public void notifyFollow(UserEntity actor, Long followedUserId) {
        try {
            NotificationEntity n = new NotificationEntity();
            n.setRecipientUserId(followedUserId);
            n.setActorUserId(actor.getId());
            n.setType("FOLLOW");
            n.setMessage(actor.getDisplayName() + " 关注了你");
            n.setIsMarkdown(false);
            n.setIsRead(false);
            n.setCreatedAt(LocalDateTime.now());
            notificationMapper.insert(n);
            notificationWebSocketHandler.pushNotification(followedUserId, n);
        } catch (Exception e) {
            log.warn("Failed to create follow notification", e);
        }
    }

    public void notifyResonance(UserEntity actor, Long diaryId, Long recipientUserId, String diarySnippet) {
        try {
            NotificationEntity n = new NotificationEntity();
            n.setRecipientUserId(recipientUserId);
            n.setActorUserId(actor.getId());
            n.setDiaryId(diaryId);
            n.setType("RESONANCE");
            n.setMessage(actor.getDisplayName() + "给你的日记《" + diarySnippet + "》点了个赞");
            n.setIsMarkdown(false);
            n.setIsRead(false);
            n.setCreatedAt(LocalDateTime.now());
            notificationMapper.insert(n);
            notificationWebSocketHandler.pushNotification(recipientUserId, n);
        } catch (Exception e) {
            log.warn("Failed to create resonance notification", e);
        }
    }

    public void notifyDailyFollowUp(Long recipientUserId, String message) {
        try {
            NotificationEntity n = new NotificationEntity();
            n.setRecipientUserId(recipientUserId);
            n.setActorUserId(null);
            n.setType("SYSTEM");
            n.setMessage(message);
            n.setIsMarkdown(true);
            n.setIsRead(false);
            n.setCreatedAt(LocalDateTime.now());
            notificationMapper.insert(n);
            notificationWebSocketHandler.pushNotification(recipientUserId, n);
        } catch (Exception e) {
            log.warn("Failed to create daily follow-up notification", e);
        }
    }

    public void notifyEncouragement(Long diaryId, Long recipientUserId, String message) {
        try {
            NotificationEntity n = new NotificationEntity();
            n.setRecipientUserId(recipientUserId);
            n.setDiaryId(diaryId);
            n.setType("ENCOURAGEMENT");
            String preview = message != null && message.length() > 30
                    ? message.substring(0, 30) + "..."
                    : message;
            n.setMessage("有人给你的日记送来了鼓励：" + preview);
            n.setIsMarkdown(false);
            n.setIsRead(false);
            n.setCreatedAt(LocalDateTime.now());
            notificationMapper.insert(n);
            notificationWebSocketHandler.pushNotification(recipientUserId, n);
        } catch (Exception e) {
            log.warn("Failed to create encouragement notification", e);
        }
    }

    public void notifyGlobalEvent(Long recipientUserId, String type, String message) {
        try {
            notificationWebSocketHandler.pushGlobalEvent(recipientUserId, type, message);
        } catch (Exception e) {
            log.warn("Failed to push global event notification", e);
        }
    }
}
