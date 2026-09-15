package com.moodcopilot.ai;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 分片与工具事件的时间线顺序。
 * <p>
 * 这条顺序很容易在重构里被顺手改掉，而且改坏之后**不会报错**：回答照常流式，只是
 * 「已检索 N 条记录」那张卡要等回答全部结束才冒出来。用户看到的是「检索框怎么最后才弹」，
 * 而日志里一切正常 —— 所以拿一个测试钉住它。
 */
class ChatTimelineTest {

    /** 模型边吐字边跑工具：真实的图就是这样，分片流一被订阅就同步跑完。 */
    private static List<String> collect(boolean sinkFirst) {
        Sinks.Many<String> toolEvents = Sinks.many().unicast().onBackpressureBuffer();
        Flux<String> chunks = Flux.create(sink -> {
            sink.next("回答前半 ");
            toolEvents.tryEmitNext("[[TOOL_EVENT]]{\"items\":[]}");
            sink.next("回答后半");
            toolEvents.tryEmitComplete();
            sink.complete();
        });

        List<String> seen = new ArrayList<>();
        Flux<String> timeline = sinkFirst
                ? ChatService.timeline(chunks, toolEvents)
                : Flux.merge(chunks, toolEvents.asFlux());   // 反过来的写法，专门用来证明它确实会错
        timeline.doOnNext(seen::add).blockLast();
        return seen;
    }

    @Test
    void toolEventsReachTheClientWhileTheAnswerIsStillStreaming() {
        assertEquals(List.of("回答前半 ", "[[TOOL_EVENT]]{\"items\":[]}", "回答后半"), collect(true));
    }

    @Test
    void puttingTheChunksFirstWouldDragThemToTheEnd() {
        // 留个反例：说明为什么不能图省事写成 merge(chunks, toolEvents)
        assertEquals(List.of("回答前半 ", "回答后半", "[[TOOL_EVENT]]{\"items\":[]}"), collect(false));
    }
}
