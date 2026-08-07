package com.joveo.supply.web;

import com.joveo.supply.ai.dto.BriefParseResponse;
import com.joveo.supply.ai.BriefParser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BriefController {

    private final BriefParser parser;

    public BriefController(BriefParser parser) {
        this.parser = parser;
    }

    @PostMapping("/parse-brief")
    public BriefParseResponse parse(@RequestBody BriefRequest request) {
        return parser.parse(request.brief());
    }

    public record BriefRequest(String brief) {
    }
}
