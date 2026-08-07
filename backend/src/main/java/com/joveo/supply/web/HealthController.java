package com.joveo.supply.web;

import org.apache.coyote.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @GetMapping("/health")
    ResponseEntity<String> healthCheck(){
        return ResponseEntity.ok("Application is up");
    }

    @GetMapping
    ResponseEntity<String> noPathCheck(){
        return ResponseEntity.ok("API is up, but this is invalid path");
    }
}
