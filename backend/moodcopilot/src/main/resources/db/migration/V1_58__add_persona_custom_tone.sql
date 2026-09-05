ALTER TABLE user_personas
    ADD COLUMN custom_tone VARCHAR(160) NULL AFTER custom_description;

ALTER TABLE conversation_persona_overrides
    ADD COLUMN custom_tone VARCHAR(160) NULL AFTER custom_description;
