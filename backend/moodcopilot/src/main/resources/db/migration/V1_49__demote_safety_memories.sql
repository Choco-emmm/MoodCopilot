UPDATE user_profile_memory
SET memory_type = 'short_term_state',
    is_core = FALSE
WHERE memory_type = 'short_term_state'
   OR LOWER(CONCAT(attribute_key, ' ', attribute_value)) REGEXP '自杀|自残|轻生|想死|不想活|结束生命|伤害自己|割腕|跳楼|心理危机|危机干预';

UPDATE user_memory_candidates
SET memory_type = 'short_term_state',
    is_core = FALSE
WHERE memory_type = 'short_term_state'
   OR LOWER(CONCAT(attribute_key, ' ', attribute_value)) REGEXP '自杀|自残|轻生|想死|不想活|结束生命|伤害自己|割腕|跳楼|心理危机|危机干预';
