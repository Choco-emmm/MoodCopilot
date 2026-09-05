ALTER TABLE user_personas
    ADD COLUMN custom_response_style VARCHAR(800) NULL AFTER custom_tone,
    ADD COLUMN disabled_behavior_flags_json JSON NULL AFTER behavior_flags_json;

ALTER TABLE conversation_persona_overrides
    ADD COLUMN custom_response_style VARCHAR(800) NULL AFTER custom_tone,
    ADD COLUMN disabled_behavior_flags_json JSON NULL AFTER behavior_flags_json;
