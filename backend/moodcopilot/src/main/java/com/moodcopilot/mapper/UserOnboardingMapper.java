package com.moodcopilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moodcopilot.entity.UserOnboardingEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface UserOnboardingMapper extends BaseMapper<UserOnboardingEntity> {

    /**
     * Concurrency-safe monotonic upsert:
     * COMPLETED > SKIPPED > IN_PROGRESS.
     * Existing COMPLETED status is terminal; SKIPPED cannot regress to IN_PROGRESS.
     * Avoids deprecated VALUES() syntax for MySQL 8+ and H2 MODE=MySQL compatibility.
     * Note: MySQL 8 evaluates ON DUPLICATE KEY UPDATE assignments left-to-right, so
     * updated_at and completed_at MUST be computed before status is mutated.
     */
    @Insert("""
            INSERT INTO user_onboarding (user_id, guide_key, guide_version, scope, status, completed_at, updated_at)
            VALUES (#{userId}, #{guideKey}, #{guideVersion}, #{scope}, #{status}, #{completedAt}, #{updatedAt})
            ON DUPLICATE KEY UPDATE
                updated_at = CASE
                    WHEN user_onboarding.status = 'COMPLETED' THEN user_onboarding.updated_at
                    WHEN user_onboarding.status = 'SKIPPED' AND #{status} = 'IN_PROGRESS' THEN user_onboarding.updated_at
                    WHEN user_onboarding.status = #{status} THEN user_onboarding.updated_at
                    ELSE #{updatedAt}
                END,
                completed_at = CASE
                    WHEN user_onboarding.status = 'COMPLETED' THEN
                        CASE
                            WHEN #{completedAt} IS NOT NULL AND user_onboarding.completed_at IS NOT NULL
                                 AND #{completedAt} < user_onboarding.completed_at THEN #{completedAt}
                            ELSE COALESCE(user_onboarding.completed_at, #{completedAt})
                        END
                    WHEN #{status} = 'COMPLETED' THEN COALESCE(user_onboarding.completed_at, #{completedAt})
                    ELSE user_onboarding.completed_at
                END,
                status = CASE
                    WHEN user_onboarding.status = 'COMPLETED' THEN 'COMPLETED'
                    WHEN #{status} = 'COMPLETED' THEN 'COMPLETED'
                    WHEN user_onboarding.status = 'SKIPPED' THEN 'SKIPPED'
                    WHEN #{status} = 'SKIPPED' THEN 'SKIPPED'
                    ELSE #{status}
                END
            """)
    int upsertMonotonic(@Param("userId") Long userId,
                        @Param("guideKey") String guideKey,
                        @Param("guideVersion") Integer guideVersion,
                        @Param("scope") String scope,
                        @Param("status") String status,
                        @Param("completedAt") LocalDateTime completedAt,
                        @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * O(1) indexed existence probe on diaries(author_user_id, created_at) for FIRST_DIARY backfill.
     */
    @Select("SELECT 1 FROM diaries WHERE author_user_id = #{userId} AND is_deleted = 0 LIMIT 1")
    Integer existsActiveDiaryForUser(@Param("userId") Long userId);
}
