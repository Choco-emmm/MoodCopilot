package com.moodcopilot.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.mapper.UserMapper;
import com.moodcopilot.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminReportController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminReportControllerMockMvcTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminReportService adminReportService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserMapper userMapper;

    @Test
    void listReportsReturnsPagedItems() throws Exception {
        Page<AdminReportView> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(new AdminReportView(
                1L, 2L, "DIARY", 7L, "违规", "PENDING", null, null,
                null, LocalDateTime.now()
        )));
        when(adminReportService.list("PENDING", 1, 20)).thenReturn(page);

        mockMvc.perform(get("/api/admin/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("PENDING"));
    }

    @Test
    void rejectDelegatesToService() throws Exception {
        mockMvc.perform(post("/api/admin/reports/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("note", "不是违规"))))
                .andExpect(status().isOk());

        verify(adminReportService).reject(1L, "不是违规");
    }

    @Test
    void hideTargetDelegatesToService() throws Exception {
        mockMvc.perform(post("/api/admin/reports/1/hide-target")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("note", "违规"))))
                .andExpect(status().isOk());

        verify(adminReportService).hideTarget(1L, "违规");
    }

    @Test
    void resolveDelegatesToService() throws Exception {
        mockMvc.perform(post("/api/admin/reports/1/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("note", "已处理"))))
                .andExpect(status().isOk());

        verify(adminReportService).resolve(1L, "已处理");
    }
}
