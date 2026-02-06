package com.syh.chat.controller;

import com.syh.chat.dto.IngestTaskResponse;
import com.syh.chat.service.AsyncIngestTaskService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/knowledge")
public class IngestTaskController {

    private final AsyncIngestTaskService asyncIngestTaskService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public IngestTaskController(AsyncIngestTaskService asyncIngestTaskService) {
        this.asyncIngestTaskService = asyncIngestTaskService;
    }

    private Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    @PostMapping(value = "/documents/upload-async", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IngestTaskResponse> uploadAsync(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            HttpServletRequest httpRequest
    ) {
        Long userId = getUserId(httpRequest);
        return ResponseEntity.ok(asyncIngestTaskService.submit(userId, file, title));
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<IngestTaskResponse> getTask(@PathVariable("taskId") String taskId, HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        return ResponseEntity.ok(asyncIngestTaskService.get(userId, taskId));
    }

    @GetMapping(value = "/tasks/{taskId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter taskEvents(@PathVariable("taskId") String taskId, HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        SseEmitter emitter = new SseEmitter(0L);
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                IngestTaskResponse status = asyncIngestTaskService.get(userId, taskId);
                emitter.send(SseEmitter.event().name("status").data(status));
                if ("SUCCEEDED".equalsIgnoreCase(status.getStatus()) || "FAILED".equalsIgnoreCase(status.getStatus())) {
                    emitter.complete();
                }
            } catch (IOException e) {
                emitter.completeWithError(e);
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }, 0, 1, TimeUnit.SECONDS);

        emitter.onCompletion(() -> future.cancel(true));
        emitter.onTimeout(() -> future.cancel(true));
        emitter.onError(ex -> future.cancel(true));
        return emitter;
    }
}
