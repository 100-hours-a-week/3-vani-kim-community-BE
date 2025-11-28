package com.vani.week4.backend.infra;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author vani
 * @since 11/28/25
 */
@RestController
public class HealthController {
    @GetMapping("/health")
    public String health(){
        return "OK";
    }
}
