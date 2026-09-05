package com.moodcopilot.ai;

import org.springframework.stereotype.Component;

/** Accepts only explicit request fields; ordinary message text is never reinterpreted as Persona. */
@Component
public class TurnPersonaOverrideResolver {
    public CurrentTurnPreference resolve(CurrentTurnPreference preference) {
        if (preference == null || !preference.isPresent()) return null;
        return new CurrentTurnPreference(
                PersonaPolicy.normalizeCustomResponseStyle(preference.temporaryResponseStyle()),
                PersonaPolicy.normalizeValues(preference.enabledBehaviorFlags(), PersonaPolicy.BEHAVIORS),
                PersonaPolicy.normalizeValues(preference.disabledBehaviorFlags(), PersonaPolicy.BEHAVIORS),
                PersonaPolicy.normalizeOutputRequirement(preference.outputRequirement()));
    }

    /** Deprecated compatibility bridge. A chat message is not a turn Persona override. */
    @Deprecated(forRemoval = false)
    public PersonaCompiler.PersonaUpdateRequestLike resolve(String message) {
        return null;
    }
}
