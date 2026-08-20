package com.clutch.lolesports.api;

import com.clutch.lolesports.source.ExternalSourceMode;
import com.clutch.lolesports.source.ExternalSourceStatus;
import com.clutch.lolesports.source.ExternalSourceSwitchException;
import com.clutch.lolesports.source.ExternalSourceSwitchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 운영자가 외부 데이터 소스를 전환하는 API.
 *
 * 이 API의 접근 제어는 현재 운영 환경의 노출 경로와 운영자 화면 경고에 맡긴다.
 */
@RestController
@ConditionalOnProperty(prefix = "external-source", name = "enabled", havingValue = "true")
@RequestMapping("/api/operator/external-source")
public class ExternalSourceController {

    private final ExternalSourceSwitchService sourceSwitchService;

    public ExternalSourceController(ExternalSourceSwitchService sourceSwitchService) {
        this.sourceSwitchService = sourceSwitchService;
    }

    @GetMapping
    public ExternalSourceStatus status() {
        return sourceSwitchService.currentStatus();
    }

    @PutMapping
    public ResponseEntity<?> switchSource(@RequestBody ChangeExternalSourceRequest request) {
        try {
            return ResponseEntity.ok(sourceSwitchService.switchTo(request.mode()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        } catch (ExternalSourceSwitchException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", exception.getMessage()));
        }
    }

    public record ChangeExternalSourceRequest(ExternalSourceMode mode) {
    }
}
