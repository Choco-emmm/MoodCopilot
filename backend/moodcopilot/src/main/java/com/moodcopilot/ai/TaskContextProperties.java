package com.moodcopilot.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** Optional configuration for deterministic task classification rules. */
@ConfigurationProperties(prefix = "moodcopilot.task-context")
public class TaskContextProperties {
    private List<Rule> rules = new ArrayList<>();

    public List<Rule> getRules() {
        return rules;
    }

    public void setRules(List<Rule> rules) {
        this.rules = rules == null ? new ArrayList<>() : new ArrayList<>(rules);
    }

    public static class Rule {
        private String taskType;
        private int priority;
        private String instruction;
        private List<String> triggerPatterns = new ArrayList<>();
        private List<String> exclusions = new ArrayList<>();
        private List<String> outputHints = new ArrayList<>();

        public String getTaskType() { return taskType; }
        public void setTaskType(String taskType) { this.taskType = taskType; }
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
        public String getInstruction() { return instruction; }
        public void setInstruction(String instruction) { this.instruction = instruction; }
        public List<String> getTriggerPatterns() { return triggerPatterns; }
        public void setTriggerPatterns(List<String> triggerPatterns) {
            this.triggerPatterns = triggerPatterns == null ? new ArrayList<>() : new ArrayList<>(triggerPatterns);
        }
        public List<String> getExclusions() { return exclusions; }
        public void setExclusions(List<String> exclusions) {
            this.exclusions = exclusions == null ? new ArrayList<>() : new ArrayList<>(exclusions);
        }
        public List<String> getOutputHints() { return outputHints; }
        public void setOutputHints(List<String> outputHints) {
            this.outputHints = outputHints == null ? new ArrayList<>() : new ArrayList<>(outputHints);
        }
    }
}
