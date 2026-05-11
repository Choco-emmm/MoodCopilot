package com.moodcopilot.report;

import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.entity.UserReportEntity;
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

@Service
public class ReportService {

    private static final Set<String> SUPPORTED_TARGETS = Set.of("DIARY", "COMMENT");

    private final UserReportMapper userReportMapper;

    public ReportService(UserReportMapper userReportMapper) {
        this.userReportMapper = userReportMapper;
    }

    @Transactional
    public void create(CreateReportRequest request) {
        String targetType = normalizeTargetType(request.targetType());
        if (request.targetId() == null || request.targetId() <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "举报对象不存在");
        }
        String reason = normalizeReason(request.reason());

        UserReportEntity report = new UserReportEntity();
        report.setReporterUserId(currentUser().getId());
        report.setTargetType(targetType);
        report.setTargetId(request.targetId());
        report.setReason(reason);
        report.setStatus("PENDING");
        report.setCreatedAt(LocalDateTime.now());
        userReportMapper.insert(report);
    }

    private String normalizeTargetType(String targetType) {
        if (targetType == null || targetType.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "请选择举报对象类型");
        }
        String normalized = targetType.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_TARGETS.contains(normalized)) {
            throw new ResponseStatusException(BAD_REQUEST, "举报对象类型只能是 DIARY 或 COMMENT");
        }
        return normalized;
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "请填写举报原因");
        }
        String normalized = reason.trim();
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }

    private UserEntity currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserEntity user) {
            return user;
        }
        throw new ResponseStatusException(BAD_REQUEST, "用户未登录");
    }
}
