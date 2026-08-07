package com.joveo.supply.domain.dto;

import com.joveo.supply.scoring.WeightProfile;

import java.util.List;
import java.util.Map;

public record RecommendationResponse(
        List<Recommendation> recommendations,
        List<ExcludedChannel> excluded,
        WeightProfile weightProfile,
        Map<String, Double> weights,
        String weightProfileRationale
) {
}
