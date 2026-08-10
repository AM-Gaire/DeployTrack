package com.deploytrack.service;

import com.deploytrack.dto.LogResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// Holds the open SSE connections and fans log entries out to them.
//
// Deliberate limitation: this registry is in-memory, so it only reaches
// clients connected to THIS instance. Behind a load balancer with several
// instances, a subscriber on instance A would never see logs written on
// instance B. Solving that properly needs a shared broker (Redis pub/sub, or
// a message queue) publishing to every instance. That is real work for a
// scale this project does not have, so the single-instance limitation is
// accepted and documented rather than pretended away.
@Service
public class LogStreamService {

    private static final Logger log = LoggerFactory.getLogger(LogStreamService.class);

    // Emitters live far longer than a request and are touched by both request
    // threads (subscribe/unsubscribe) and the async deployment threads that
    // publish events, so every structure here has to be thread-safe.
    // CopyOnWriteArraySet suits the access pattern: reads on every log line,
    // writes only when a client connects or drops.
    private final Map<Long, Set<SseEmitter>> emittersByDeployment = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long deploymentId, long timeoutMillis) {
        var emitter = new SseEmitter(timeoutMillis);

        emittersByDeployment
            .computeIfAbsent(deploymentId, id -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
            .add(emitter);

        // All three callbacks must deregister. Without them the map grows
        // forever: every browser tab that ever opened a stream stays
        // referenced, and the JVM leaks until it dies.
        emitter.onCompletion(() -> remove(deploymentId, emitter));
        emitter.onTimeout(() -> remove(deploymentId, emitter));
        emitter.onError(throwable -> remove(deploymentId, emitter));

        return emitter;
    }

    // Runs only once the surrounding transaction has committed. Pushing from
    // inside the transaction would let subscribers see a log line that a
    // rollback then erases -- visible to the user as a line that appears live
    // and disappears on refresh.
    @TransactionalEventListener
    public void onLogEntryCreated(LogEntryCreatedEvent event) {
        send(event.deploymentId(), "log", event.log());
    }

    // Tells subscribers the deployment has settled so the client can close
    // deliberately, rather than holding a connection open until it times out.
    @TransactionalEventListener
    public void onDeploymentCompleted(DeploymentCompletedEvent event) {
        completeStream(event.deploymentId(), event.status().name());
    }

    public void completeStream(Long deploymentId, String finalStatus) {
        send(deploymentId, "deployment-complete", Map.of("status", finalStatus));

        Set<SseEmitter> emitters = emittersByDeployment.remove(deploymentId);
        if (emitters != null) {
            emitters.forEach(SseEmitter::complete);
        }
    }

    private void send(Long deploymentId, String eventName, Object payload) {
        Set<SseEmitter> emitters = emittersByDeployment.get(deploymentId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException | IllegalStateException ex) {
                // A client that closed its tab is the normal case, not an
                // error worth logging loudly. Drop the dead emitter and carry
                // on -- one broken connection must not stop delivery to the
                // others.
                remove(deploymentId, emitter);
            }
        }
    }

    private void remove(Long deploymentId, SseEmitter emitter) {
        emittersByDeployment.computeIfPresent(deploymentId, (id, emitters) -> {
            emitters.remove(emitter);
            // Returning null removes the key, so the map does not accumulate
            // empty sets for every deployment ever streamed.
            return emitters.isEmpty() ? null : emitters;
        });
    }

    // Exposed for tests and diagnostics: a number that only grows is the
    // signature of a leaking registry.
    public int activeStreamCount() {
        return emittersByDeployment.values().stream().mapToInt(Set::size).sum();
    }

    public int subscriberCount(Long deploymentId) {
        return emittersByDeployment.getOrDefault(deploymentId, Set.of()).size();
    }
}
