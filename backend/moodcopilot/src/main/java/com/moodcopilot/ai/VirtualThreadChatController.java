package com.moodcopilot.ai;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/vthread")
public class VirtualThreadChatController {

    /**
     * 极简版虚拟线程 AI 聊天接口
     * produces 必须声明为 text/event-stream 才能触发浏览器的 SSE 接收机制
     */
    @GetMapping(value = "/chat", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chat(@RequestParam(defaultValue = "测试") String message) {
        
        // 1. 创建一个 SseEmitter 发射器（0L 表示连接永不超时）
        SseEmitter emitter = new SseEmitter(0L);
        
        // 2. 开启一个轻如鸿毛的虚拟线程去干脏活累活
        Thread.startVirtualThread(() -> {
            try {
                // ============== 纯同步的极简业务逻辑开始 ==============
                
                // 模拟一个大模型的同步阻塞式返回流
                List<String> mockAiChunks = List.of(
                        "你", "好", "，", 
                        "你", "说", "了", "：", "「", message, "」", "。", 
                        "这", "是", "由", "虚", "拟", "线", "程", "发", "送", "的", "流", "式", "响", "应", "！"
                );
                
                // 直接写最普通的 for 循环！不需要任何响应式包装！
                for (String chunk : mockAiChunks) {
                    
                    // 模拟大模型思考的 300 毫秒网络延迟
                    // 在虚拟线程里碰到 sleep，JVM 会把底层的物理线程抽走去服务别人，当前虚拟线程被安全挂起，性能 0 损耗！
                    Thread.sleep(300); 
                    
                    // 拿到一个字，同步发射给前端（流式输出）
                    emitter.send(chunk);
                }
                
                // ============== 纯同步的极简业务逻辑结束 ==============
                
                // 发送完毕，通知前端关闭连接
                emitter.complete();
                
            } catch (Exception e) {
                // 原生的 try-catch 捕获所有异常
                emitter.completeWithError(e);
            }
        });

        // 3. 主线程瞬间执行完并 return，此时 HTTP 长连接建立，开始接收虚拟线程发来的数据
        return emitter;
    }
}
