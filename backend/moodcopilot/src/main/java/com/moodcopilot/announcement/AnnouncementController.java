package com.moodcopilot.announcement;

import com.moodcopilot.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping("/api/announcements/active")
    public ApiResponse<AnnouncementView> active() {
        return ApiResponse.ok(announcementService.active());
    }

    @GetMapping("/api/admin/announcements/current")
    public ApiResponse<AnnouncementView> current() {
        return ApiResponse.ok(announcementService.currentForAdmin());
    }

    @PostMapping("/api/admin/announcements/publish")
    public ApiResponse<AnnouncementView> publish(@RequestBody PublishAnnouncementRequest request) {
        return ApiResponse.ok(announcementService.publish(request));
    }
}
