package com.joveo.supply.web;

import com.joveo.supply.domain.dto.CampaignRequest;
import com.joveo.supply.domain.entity.Channel;
import com.joveo.supply.domain.dto.RecommendationResponse;
import com.joveo.supply.repo.ChannelRepository;
import com.joveo.supply.scoring.ScoringService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class RecommendationController {

    private final ChannelRepository channels;
    private final ScoringService scoring;

    public RecommendationController(ChannelRepository channels, ScoringService scoring) {
        this.channels = channels;
        this.scoring = scoring;
    }

    @PostMapping("/recommendations")
    public RecommendationResponse recommend(@Valid @RequestBody CampaignRequest request) {
        return scoring.recommend(channels.findAll(), request);
    }

    @GetMapping("/channels")
    public List<Channel> channels() {
        return channels.findAll();
    }
}
