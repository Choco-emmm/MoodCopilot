package com.moodcopilot.auth;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.common.response.ApiResponse;
import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/captcha")
public class CaptchaController {

    private final ImageCaptchaApplication imageCaptchaApplication;

    public CaptchaController(ImageCaptchaApplication imageCaptchaApplication) {
        this.imageCaptchaApplication = imageCaptchaApplication;
    }

    @RequestMapping(value = "/gen", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse<ImageCaptchaVO> genCaptcha(@RequestParam(value = "type", required = false) String type) {
        if (type == null || type.isBlank()) {
            type = "SLIDER";
        }
        return imageCaptchaApplication.generateCaptcha(type);
    }

    public static class CheckCaptchaParam {
        private String id;
        private ImageCaptchaTrack data;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public ImageCaptchaTrack getData() { return data; }
        public void setData(ImageCaptchaTrack data) { this.data = data; }
    }

    @PostMapping("/check")
    public ApiResponse<?> checkCaptcha(@RequestBody CheckCaptchaParam param) {
        ApiResponse<?> match = imageCaptchaApplication.matching(param.getId(), param.getData());
        if (match.isSuccess()) {
            return ApiResponse.ofSuccess(param.getId());
        }
        return match;
    }
}
