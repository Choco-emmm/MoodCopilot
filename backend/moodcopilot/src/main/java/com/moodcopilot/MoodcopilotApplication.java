package com.moodcopilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class MoodcopilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoodcopilotApplication.class, args);
    }

}
