package com.clutch.replay.api;

import com.clutch.replay.service.ReplayControlException;
import com.clutch.replay.service.ReplayControlService;
import com.clutch.replay.service.ReplayStartResult;
import com.clutch.replay.service.ReplaySourceModeException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** replay 제어가 활성화된 환경에서만 노출되는 재생 제어 API. */
@RestController
@ConditionalOnProperty(prefix = "replay", name = "enabled", havingValue = "true")
@RequestMapping("/api/replay")
public class ReplayController {

    private final ReplayControlService replayControlService;

    public ReplayController(ReplayControlService replayControlService) {
        this.replayControlService = replayControlService;
    }

    /** 새 외부 matchId/gameId 목록으로 fixture 재생을 처음부터 시작한다. */
    @PostMapping("/start")
    public ResponseEntity<?> start() {
        try {
            ReplayStartResult result = replayControlService.start();
            return ResponseEntity.ok(result);
        } catch (ReplaySourceModeException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", exception.getMessage()));
        } catch (ReplayControlException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", exception.getMessage()));
        }
    }

    /** 현재 JSONL fixture 타임라인의 재생 위치를 반환한다. */
    @GetMapping("/status")
    public ResponseEntity<?> status() {
        try {
            return ResponseEntity.ok(replayControlService.status());
        } catch (ReplayControlException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", exception.getMessage()));
        }
    }

    /** 재생을 멈추지 않고 JSONL 타임라인의 배속을 변경한다. */
    @PostMapping("/speed")
    public ResponseEntity<?> changeSpeed(@RequestParam double value) {
        if (value < 1 || value > ReplayControlService.MAX_REPLAY_SPEED) {
            return ResponseEntity.badRequest().body(Map.of("error", "배속은 1 이상 60 이하여야 한다"));
        }
        try {
            return ResponseEntity.ok(replayControlService.changeSpeed(value));
        } catch (ReplayControlException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", exception.getMessage()));
        }
    }
}
