package com.vani.week4.backend.infra;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author vani
 * @since 11/21/25
 */
@RestController
public class HealthController {
    @GetMapping("/health")
    public String healthCheck() {
        return "I'm alive"; // 200 OK만 뱉으면 됩니다.
    }
}
