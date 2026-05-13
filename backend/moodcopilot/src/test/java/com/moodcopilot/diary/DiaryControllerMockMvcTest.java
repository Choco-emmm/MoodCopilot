package com.moodcopilot.diary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.mapper.UserMapper;
import com.moodcopilot.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DiaryController.class)
@AutoConfigureMockMvc(addFilters = false)
class DiaryControllerMockMvcTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DiaryService diaryService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserMapper userMapper;

    @Test
    void createReturnsDiary() throws Exception {
        DiaryView diary = sampleDiary(1L);
        when(diaryService.create(any(CreateDiaryRequest.class))).thenReturn(diary);

        mockMvc.perform(post("/api/diaries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateDiaryRequest("今天有点累", "PUBLIC"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.analysis.moodLabel").value("疲惫"));

        verify(diaryService).create(any(CreateDiaryRequest.class));
        verify(diaryService).runAiAnalysis(eq(1L), eq(1L), eq("今天很累"));
    }

    @Test
    void createReturnsBadRequestWhenServiceRejects() throws Exception {
        when(diaryService.create(any(CreateDiaryRequest.class)))
                .thenThrow(new ResponseStatusException(BAD_REQUEST, "请先写下今天的情绪"));

        mockMvc.perform(post("/api/diaries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateDiaryRequest(" ", "PUBLIC"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void myDiariesReturnsPagedItems() throws Exception {
        when(diaryService.myDiaries(1, 20)).thenReturn(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<DiaryView>(1, 20, 2)
                        .setRecords(List.of(sampleDiary(1L), sampleDiary(2L)))
        );

        mockMvc.perform(get("/api/diaries/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    void publicDiariesReturnsList() throws Exception {
        when(diaryService.publicDiaries(anyInt(), anyInt())).thenReturn(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<DiaryView>(1, 20)
                        .setRecords(List.of(sampleDiary(3L)))
        );

        mockMvc.perform(get("/api/diaries/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].id").value(3));
    }

    @Test
    void getReturnsNotFoundWhenDiaryMissing() throws Exception {
        when(diaryService.get(99L)).thenThrow(new ResponseStatusException(NOT_FOUND, "日记不存在"));

        mockMvc.perform(get("/api/diaries/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void similarUsesLimitQuery() throws Exception {
        when(diaryService.similar(1L, 5)).thenReturn(List.of(sampleDiary(2L)));

        mockMvc.perform(get("/api/diaries/1/similar").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        verify(diaryService).similar(1L, 5);
    }

    @Test
    void similarUsesDefaultLimitWhenQueryMissing() throws Exception {
        when(diaryService.similar(anyLong(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/diaries/1/similar"))
                .andExpect(status().isOk());

        verify(diaryService).similar(1L, 3);
    }

    @Test
    void weeklyReportReturnsInsightsAndSuggestions() throws Exception {
        WeeklyReportView report = new WeeklyReportView(
                "5/4 - 5/10",
                1,
                List.of(),
                java.util.Map.of("工作学习", 1),
                "这周你很努力。",
                List.of("疲惫主要集中在工作学习之后"),
                List.of("今晚给自己 20 分钟离线休息"),
                "我想继续聊聊这周的疲惫从哪里来。"
        );
        when(diaryService.weeklyReport(0)).thenReturn(report);

        mockMvc.perform(get("/api/diaries/weekly-report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.insights[0]").value("疲惫主要集中在工作学习之后"))
                .andExpect(jsonPath("$.data.suggestions[0]").value("今晚给自己 20 分钟离线休息"))
                .andExpect(jsonPath("$.data.followUpPrompt").value("我想继续聊聊这周的疲惫从哪里来。"));
    }

    @Test
    void hideDiaryDelegatesToService() throws Exception {
        mockMvc.perform(post("/api/diaries/7/hide"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(diaryService).hideDiary(7L);
    }

    @Test
    void addCommentReturnsUpdatedDiary() throws Exception {
        DiaryView withComment = new DiaryView(
                1L,
                1L,
                "同频的人",
                "辛苦了",
                DiaryVisibility.PUBLIC,
                new DiaryAnalysis("委屈", 3, List.of("人际关系"), "辛苦了", "你并不孤单"),
                LocalDateTime.now(),
                1,
                List.of(new DiaryComment(1L, null, null, "陌生人", "抱抱你", LocalDateTime.now(), List.of()))
        );
        when(diaryService.addComment(anyLong(), any(CreateCommentRequest.class))).thenReturn(withComment);

        mockMvc.perform(post("/api/diaries/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest("抱抱你", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comments.length()").value(1))
                .andExpect(jsonPath("$.data.comments[0].authorName").value("陌生人"));
    }

    @Test
    void addCommentReturnsBadRequestWhenBodyMissing() throws Exception {
        mockMvc.perform(post("/api/diaries/1/comments")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resonateReturnsUpdatedDiary() throws Exception {
        DiaryView updated = new DiaryView(
                1L,
                1L,
                "同频的人",
                "辛苦了",
                DiaryVisibility.PUBLIC,
                new DiaryAnalysis("委屈", 3, List.of("人际关系"), "辛苦了", "你并不孤单"),
                LocalDateTime.now(),
                4,
                List.of()
        );
        when(diaryService.resonate(1L)).thenReturn(updated);

        mockMvc.perform(post("/api/diaries/1/resonance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resonanceCount").value(4));
    }

    private DiaryView sampleDiary(long id) {
        return new DiaryView(
                id,
                id,
                "同频的人",
                "今天很累",
                DiaryVisibility.PUBLIC,
                new DiaryAnalysis("疲惫", 3, List.of("工作学习"), "今天很累", "先休息一下"),
                LocalDateTime.now(),
                1,
                List.of()
        );
    }
}
