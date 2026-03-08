package com.ming.agent12306.controller;

import com.ming.agent12306.model.request.AssistantChatRequest;
import com.ming.agent12306.model.response.AssistantChatResponse;
import com.ming.agent12306.model.response.AssistantStreamEvent;
import com.ming.agent12306.service.AssistantService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @GetMapping
    public String index() {
        return "12306 assistant service is ready.";
    }

    @PostMapping("/chat")
    public AssistantChatResponse chat(@Valid @RequestBody AssistantChatRequest request) {
        return assistantService.chat(request);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@Valid @RequestBody AssistantChatRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        assistantService.streamChat(request).subscribe(
                event -> sendEvent(emitter, event),
                ex -> {
                    sendErrorEvent(emitter, ex);
                    emitter.complete();
                },
                emitter::complete
        );
        return emitter;
    }

    private void sendEvent(SseEmitter emitter, AssistantStreamEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.type())
                    .data(event));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void sendErrorEvent(SseEmitter emitter, Throwable ex) {
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(new AssistantStreamEvent(
                            "error",
                            true,
                            "ASSISTANT",
                            ex == null || ex.getMessage() == null ? "系统繁忙，请稍后重试" : ex.getMessage(),
                            null
                    )));
        } catch (Exception ignored) {
        }
    }
}
