package com.moodcopilot.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moodcopilot.entity.DiaryCommentEntity;
import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.entity.UserReportEntity;
import com.moodcopilot.mapper.DiaryCommentMapper;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.mapper.UserMapper;
import com.moodcopilot.mapper.UserReportMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class AdminReportService {

    private static final Set<String> STATUSES = Set.of("PENDING", "RESOLVED", "REJECTED");
    private static final Set<String> TARGET_TYPES = Set.of("DIARY", "COMMENT");

    private final UserReportMapper userReportMapper;
    private final DiaryMapper diaryMapper;
    private final DiaryCommentMapper diaryCommentMapper;
    private final UserMapper userMapper;

    public AdminReportService(
            UserReportMapper userReportMapper,
            DiaryMapper diaryMapper,
            DiaryCommentMapper diaryCommentMapper,
            UserMapper userMapper) {
        this.userReportMapper = userReportMapper;
        this.diaryMapper = diaryMapper;
        this.diaryCommentMapper = diaryCommentMapper;
        this.userMapper = userMapper;
    }

    public Page<AdminReportView> list(String status, int page, int size) {
        requireAdmin();
        String normalizedStatus = normalizeStatus(status == null ? "PENDING" : status);
        long pageNumber = Math.max(1, page);
        long pageSize = Math.max(1, Math.min(50, size));

        Page<UserReportEntity> reportPage = userReportMapper.selectPage(
                new Page<>(pageNumber, pageSize),
                new LambdaQueryWrapper<UserReportEntity>()
                        .eq(UserReportEntity::getStatus, normalizedStatus)
                        .orderByDesc(UserReportEntity::getCreatedAt));

        Page<AdminReportView> viewPage = new Page<>(pageNumber, pageSize, reportPage.getTotal());
        viewPage.setRecords(reportPage.getRecords().stream().map(AdminReportView::from).toList());
        return viewPage;
    }

    @Transactional
    public void resolve(Long id, String note) {
        UserReportEntity report = findReport(id);
        finish(report, "RESOLVED", note);
    }

    @Transactional
    public void reject(Long id, String note) {
        UserReportEntity report = findReport(id);
        finish(report, "REJECTED", note);
    }

    @Transactional
    public void hideTarget(Long id, String note) {
        UserReportEntity report = findReport(id);
        String targetType = normalizeTargetType(report.getTargetType());
        if ("DIARY".equals(targetType)) {
            DiaryEntity diary = new DiaryEntity();
            diary.setId(report.getTargetId());
            diary.setIsDeleted(true);
            diaryMapper.updateById(diary);
        } else {
            DiaryCommentEntity comment = new DiaryCommentEntity();
            comment.setId(report.getTargetId());
            comment.setIsDeleted(true);
            diaryCommentMapper.updateById(comment);
        }
        finish(report, "RESOLVED", note);
    }

    @Transactional
    public void banUserAndHideTarget(Long id, String note) {
        UserReportEntity report = findReport(id);
        String targetType = normalizeTargetType(report.getTargetType());
        Long authorUserId;
        if ("DIARY".equals(targetType)) {
            DiaryEntity diary = diaryMapper.selectById(report.getTargetId());
            if (diary == null) {
                throw new ResponseStatusException(NOT_FOUND, "目标日记不存在");
            }
            authorUserId = diary.getAuthorUserId();
            diary.setIsDeleted(true);
            diaryMapper.updateById(diary);
        } else {
            DiaryCommentEntity comment = diaryCommentMapper.selectById(report.getTargetId());
            if (comment == null) {
                throw new ResponseStatusException(NOT_FOUND, "目标评论不存在");
            }
            authorUserId = comment.getAuthorUserId();
            comment.setIsDeleted(true);
            diaryCommentMapper.updateById(comment);
        }

        UserEntity author = userMapper.selectById(authorUserId);
        if (author != null) {
            author.setStatus(0);
            userMapper.updateById(author);
        }

        finish(report, "RESOLVED", "【封禁用户并隐藏内容】" + (note != null ? note : ""));
    }

    private UserReportEntity findReport(Long id) {
        requireAdmin();
        if (id == null || id <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "举报不存在");
        }
        UserReportEntity report = userReportMapper.selectById(id);
        if (report == null) {
            throw new ResponseStatusException(NOT_FOUND, "举报不存在");
        }
        return report;
    }

    private void finish(UserReportEntity report, String status, String note) {
        UserEntity admin = requireAdmin();
        report.setStatus(status);
        report.setHandledByUserId(admin.getId());
        report.setHandledAt(LocalDateTime.now());
        report.setHandleNote(normalizeNote(note));
        userReportMapper.updateById(report);
    }

    private UserEntity requireAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserEntity user
                && "ADMIN".equalsIgnoreCase(user.getRole())) {
            return user;
        }
        throw new ResponseStatusException(FORBIDDEN, "需要管理员权限");
    }

    private String normalizeStatus(String status) {
        String normalized = status == null ? "PENDING" : status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) {
            throw new ResponseStatusException(BAD_REQUEST, "举报状态不支持");
        }
        return normalized;
    }

    private String normalizeTargetType(String targetType) {
        String normalized = targetType == null ? "" : targetType.trim().toUpperCase(Locale.ROOT);
        if (!TARGET_TYPES.contains(normalized)) {
            throw new ResponseStatusException(BAD_REQUEST, "举报对象类型不支持");
        }
        return normalized;
    }

    private String normalizeNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String normalized = note.trim();
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }
}
