CREATE TABLE `diary_knowledge_graph` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `diary_id` BIGINT NOT NULL COMMENT '日记ID',
  `head_entity` VARCHAR(100) NOT NULL COMMENT '头实体(如: 老板, 周末, 睡眠)',
  `relation` VARCHAR(50) NOT NULL COMMENT '关系(如: 导致, 缓解, 属于)',
  `tail_entity` VARCHAR(100) NOT NULL COMMENT '尾实体(如: 焦虑, 压力, 快乐)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_diary_id` (`diary_id`),
  FULLTEXT INDEX `ft_head_tail` (`head_entity`, `tail_entity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='日记知识图谱三元组表';
