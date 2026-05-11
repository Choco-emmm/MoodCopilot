package com.moodcopilot.admin;

import com.moodcopilot.entity.UserReportEntity;

import java.time.LocalDateTime;

public record AdminReportView(
        Long id,
        Long reporterUserId,
        String targetType,
        Long targetId,
        String reason,
        String status,
        Long handledByUserId,
        LocalDateTime handledAt,
        String handleNote,
        LocalDateTime createdAt
) {

    public static AdminReportView from(UserReportEntity report) {
        return new AdminReportView(
                report.getId(),
                report.getReporterUserId(),
                report.getTargetType(),
                report.getTargetId(),
                report.getReason(),
                report.getStatus(),
                report.getHandledByUserId(),
                report.getHandledAt(),
                report.getHandleNote(),
                report.getCreatedAt()
        );
    }
}
