package com.moodcopilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.moodcopilot.ai.TaskContextProperties;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties(TaskContextProperties.class)
public class MoodcopilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoodcopilotApplication.class, args);
    }

}
