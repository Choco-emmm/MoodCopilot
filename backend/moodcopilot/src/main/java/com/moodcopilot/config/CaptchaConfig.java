package com.moodcopilot.config;

import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.resource.ResourceStore;
import cloud.tianai.captcha.resource.common.model.dto.Resource;
import cloud.tianai.captcha.resource.impl.LocalMemoryResourceStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CaptchaConfig {

    @Bean
    public ResourceStore resourceStore() {
        LocalMemoryResourceStore store = new LocalMemoryResourceStore();
        // 添加天爱验证码内置的默认背景图片
        store.addResource(CaptchaTypeConstant.SLIDER, new Resource("classpath", "META-INF/cut-image/resource/1.jpg"));
        return store;
    }
}
