package com.deploytrack.controller;

import com.deploytrack.dto.LogResponse;
import com.deploytrack.dto.PagedResponse;
import com.deploytrack.entity.Deployment;
import com.deploytrack.entity.LogLevel;
import com.deploytrack.repository.LogRepository;
import com.deploytrack.service.DeploymentService;
import com.deploytrack.service.LogStreamService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/deployments/{deploymentId}/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogRepository logRepository;
    private final DeploymentService deploymentService;
    private final LogStreamService logStreamService;

    @Value("${deploytrack.log-stream.timeout:5m}")
    private Duration streamTimeout;

    @GetMapping
    public PagedResponse<LogResponse> list(
        @PathVariable Long deploymentId,
        @RequestParam(required = false) LogLevel level,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        // 404 for an unknown deployment rather than an empty page.
        deploymentService.get(deploymentId);

        // Oldest first: logs are read as a narrative of what happened, unlike
        // the deployment list which is browsed newest-first.
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "timestamp", "id"));
        return PagedResponse.from(logRepository.findFiltered(deploymentId, level, pageable), LogResponse::from);
    }

    // Server-Sent Events rather than WebSocket: log delivery is one-way, so
    // full-duplex would be complexity paid for and never used. SSE is plain
    // HTTP, survives proxies, and browsers reconnect automatically via
    // EventSource.
    //
    // Returning the emitter releases the request thread immediately -- the
    // connection stays open, held by the servlet container's async support,
    // not by a blocked thread. Blocking a thread per subscriber would exhaust
    // the pool after a few dozen viewers.
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long deploymentId) {
        Deployment deployment = deploymentService.get(deploymentId);

        SseEmitter emitter = logStreamService.subscribe(deploymentId, streamTimeout.toMillis());

        // A deployment that already finished will never emit another event,
        // so close the stream at once instead of leaving the client waiting
        // for messages that cannot arrive.
        if (deployment.getStatus().isTerminal()) {
            logStreamService.completeStream(deploymentId, deployment.getStatus().name());
        }

        return emitter;
    }
}
