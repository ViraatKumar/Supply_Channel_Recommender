package com.joveo.supply.domain.dto;

import com.joveo.supply.domain.dto.constant.ChannelType;

import java.util.List;
import java.util.Map;

public record Recommendation(
        String channelId,
        String channelName,
        ChannelType channelType,
        int rank,
        double score,
        Map<String, Double> factorScores,
        long expectedCost,
        int expectedApplicants,
        double costPerApplicant,
        double qualityEstimate,
        int leadTimeDays,
        String bindingConstraint,
        String reason,
        List<String> limitations
) {
}
