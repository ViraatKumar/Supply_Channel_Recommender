package com.joveo.supply.domain.dto;

import com.joveo.supply.domain.dto.constant.Seniority;
import com.joveo.supply.scoring.WeightProfile;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CampaignRequest(
        @NotBlank String jobTitle,
        @NotBlank String location,
        @Min(1) int applicantsNeeded,
        @Positive double budget,
        @Min(1) int timelineDays,
        List<String> skills,
        @NotNull Seniority seniority,
        boolean remoteOk,
        String additionalConstraints,
        WeightProfile weightProfileOverride
) {
    public CampaignRequest {
        skills = skills == null ? List.of() : skills.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
