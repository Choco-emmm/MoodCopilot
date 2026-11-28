package com.moodcopilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moodcopilot.entity.UserOnboardingEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

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

    @Select("""
            SELECT COUNT(*)
            FROM users
            WHERE onboarding_enrolled_version >= #{guideVersion}
            """)
    Long countEligibleUsers(@Param("guideVersion") int guideVersion);

    @Select("""
            SELECT uo.guide_key AS guide_key,
                   uo.status AS status,
                   COUNT(DISTINCT uo.user_id) AS user_count
            FROM user_onboarding uo
            INNER JOIN users u ON u.id = uo.user_id
            WHERE u.onboarding_enrolled_version >= #{guideVersion}
              AND uo.guide_version = #{guideVersion}
              AND uo.scope = 'GLOBAL'
            GROUP BY uo.guide_key, uo.status
            """)
    List<KeyStatusCountRow> aggregateKeyStatusCountsForEligibleUsers(@Param("guideVersion") int guideVersion);

    @Select("""
            SELECT COUNT(*)
            FROM (
                SELECT uo.user_id
                FROM user_onboarding uo
                INNER JOIN users u ON u.id = uo.user_id
                WHERE u.onboarding_enrolled_version >= #{guideVersion}
                  AND uo.guide_version = #{guideVersion}
                  AND uo.scope = 'GLOBAL'
                  AND uo.status = 'COMPLETED'
                  AND uo.guide_key IN ('FIRST_DIARY', 'FIRST_ANALYSIS_VIEWED', 'FIRST_CHAT_REPLY', 'MEMORY_CENTER_VISITED')
                GROUP BY uo.user_id
                HAVING COUNT(DISTINCT uo.guide_key) = 4
            ) completed_core_users
            """)
    Long countAllCoreTasksCompletedForEligibleUsers(@Param("guideVersion") int guideVersion);

    @Select("""
            SELECT uo.scope AS scope,
                   COUNT(DISTINCT uo.user_id) AS user_count
            FROM user_onboarding uo
            INNER JOIN users u ON u.id = uo.user_id
            WHERE u.onboarding_enrolled_version >= #{guideVersion}
              AND uo.guide_version = #{guideVersion}
            GROUP BY uo.scope
            """)
    List<ScopeUserCountRow> aggregateScopeCountsForEligibleUsers(@Param("guideVersion") int guideVersion);

    @Select("""
            SELECT COUNT(*)
            FROM (
                SELECT uo.user_id
                FROM user_onboarding uo
                INNER JOIN users u ON u.id = uo.user_id
                WHERE u.onboarding_enrolled_version >= #{guideVersion}
                  AND uo.guide_version = #{guideVersion}
                  AND uo.scope IN ('WEB', 'MINI_PROGRAM')
                GROUP BY uo.user_id
                HAVING COUNT(DISTINCT uo.scope) >= 2
            ) cross_platform_users
            """)
    Long countCrossPlatformHintUsers(@Param("guideVersion") int guideVersion);

    class KeyStatusCountRow {
        private String guideKey;
        private String status;
        private Long userCount;

        public String getGuideKey() {
            return guideKey;
        }

        public void setGuideKey(String guideKey) {
            this.guideKey = guideKey;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Long getUserCount() {
            return userCount;
        }

        public void setUserCount(Long userCount) {
            this.userCount = userCount;
        }
    }

    class ScopeUserCountRow {
        private String scope;
        private Long userCount;

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }

        public Long getUserCount() {
            return userCount;
        }

        public void setUserCount(Long userCount) {
            this.userCount = userCount;
        }
    }
}
