CREATE TABLE diary_recommendation_exposures (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    diary_id BIGINT UNSIGNED NOT NULL,
    scene VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_recommendation_exposure_user_scene_time (user_id, scene, created_at),
    KEY idx_recommendation_exposure_diary (diary_id),
    CONSTRAINT fk_recommendation_exposure_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_recommendation_exposure_diary FOREIGN KEY (diary_id) REFERENCES diaries(id) ON DELETE CASCADE
);
