package com.moodcopilot;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Requires MySQL and Redis — use MockMvc tests for CI")
class MoodcopilotApplicationTests {

    @Test
    void contextLoads() {
    }

}
