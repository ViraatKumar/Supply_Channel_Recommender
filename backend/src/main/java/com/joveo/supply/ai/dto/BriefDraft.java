package com.joveo.supply.ai.dto;

import com.joveo.supply.domain.dto.constant.Seniority;
import com.joveo.supply.domain.dto.CampaignRequest;

import java.util.List;

public record BriefDraft(
        String jobTitle,
        String location,
        Integer applicantsNeeded,
        Double budget,
        Integer timelineDays,
        List<String> skills,
        Seniority seniority,
        Boolean remoteOk,
        String additionalConstraints,
        List<String> missingFields
) {
}
