package com.moodcopilot.announcement;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodcopilot.entity.SystemAnnouncementEntity;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.mapper.SystemAnnouncementMapper;
import com.moodcopilot.mapper.UserMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@Service
public class AnnouncementService {

    private static final int TITLE_MAX_LENGTH = 60;
    private static final int CONTENT_MAX_LENGTH = 2000;

    private final SystemAnnouncementMapper announcementMapper;
    private final UserMapper userMapper;

    public AnnouncementService(SystemAnnouncementMapper announcementMapper, UserMapper userMapper) {
        this.announcementMapper = announcementMapper;
        this.userMapper = userMapper;
    }

    public AnnouncementView active() {
        SystemAnnouncementEntity announcement = latest();
        return announcement == null ? null : toView(announcement);
    }

    public AnnouncementView currentForAdmin() {
        requireAdmin();
        return active();
    }

    @Transactional
    public synchronized AnnouncementView publish(PublishAnnouncementRequest request) {
        UserEntity admin = requireAdmin();
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "公告内容不能为空");
        }

        String title = normalize(request.title(), "公告标题", TITLE_MAX_LENGTH);
        String content = normalize(request.content(), "公告正文", CONTENT_MAX_LENGTH);
        SystemAnnouncementEntity previous = latest();

        SystemAnnouncementEntity announcement = new SystemAnnouncementEntity();
        announcement.setVersion(previous == null ? 1L : previous.getVersion() + 1L);
        announcement.setTitle(title);
        announcement.setContent(content);
        announcement.setPublishedByUserId(admin.getId());
        announcement.setPublishedAt(LocalDateTime.now());
        announcementMapper.insert(announcement);
        return AnnouncementView.from(announcement, admin.getDisplayName());
    }

    private SystemAnnouncementEntity latest() {
        return announcementMapper.selectOne(new LambdaQueryWrapper<SystemAnnouncementEntity>()
                .orderByDesc(SystemAnnouncementEntity::getVersion)
                .last("LIMIT 1"));
    }

    private AnnouncementView toView(SystemAnnouncementEntity announcement) {
        UserEntity publisher = userMapper.selectById(announcement.getPublishedByUserId());
        return AnnouncementView.from(announcement, publisher == null ? null : publisher.getDisplayName());
    }

    private UserEntity requireAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserEntity user
                && "ADMIN".equalsIgnoreCase(user.getRole())) {
            return user;
        }
        throw new ResponseStatusException(FORBIDDEN, "需要管理员权限");
    }

    private String normalize(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, fieldName + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(BAD_REQUEST, fieldName + "不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }
}
