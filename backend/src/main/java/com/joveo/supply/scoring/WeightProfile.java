package com.joveo.supply.scoring;

import com.joveo.supply.domain.dto.CampaignRequest;
import com.joveo.supply.domain.dto.constant.Seniority;

import java.util.LinkedHashMap;
import java.util.Map;

public enum WeightProfile {

    BALANCED(0.30, 0.25, 0.25, 0.15, 0.05),
    QUALITY_FIRST(0.20, 0.15, 0.45, 0.15, 0.05),
    VOLUME_FIRST(0.30, 0.40, 0.10, 0.15, 0.05);

    public static final int BULK_HIRING_THRESHOLD = 100;

    public static final String COST_EFFICIENCY = "costEfficiency";
    public static final String VOLUME_FIT = "volumeFit";
    public static final String QUALITY = "quality";
    public static final String SPEED = "speed";
    public static final String SKILL_MATCH = "skillMatch";

    private final double costEfficiency;
    private final double volumeFit;
    private final double quality;
    private final double speed;
    private final double skillMatch;

    WeightProfile(double costEfficiency, double volumeFit, double quality, double speed, double skillMatch) {
        this.costEfficiency = costEfficiency;
        this.volumeFit = volumeFit;
        this.quality = quality;
        this.speed = speed;
        this.skillMatch = skillMatch;
    }

    public static WeightProfile select(CampaignRequest request) {
        if (request.weightProfileOverride() != null) {
            return request.weightProfileOverride();
        }
        if (request.seniority() == Seniority.SENIOR || request.seniority() == Seniority.EXECUTIVE) {
            return QUALITY_FIRST;
        }
        if (request.applicantsNeeded() >= BULK_HIRING_THRESHOLD) {
            return VOLUME_FIRST;
        }
        return BALANCED;
    }

    public static String rationaleFor(CampaignRequest request, WeightProfile chosen) {
        if (request.weightProfileOverride() != null) {
            return "Weights were overridden manually to " + chosen + ".";
        }
        return switch (chosen) {
            case QUALITY_FIRST -> "%s hiring — applicant relevance weighted above cost and volume."
                    .formatted(request.seniority() == Seniority.EXECUTIVE ? "Executive" : "Senior");
            case VOLUME_FIRST -> "%d applicants needed (bulk threshold %d) — reach weighted above quality."
                    .formatted(request.applicantsNeeded(), BULK_HIRING_THRESHOLD);
            case BALANCED -> "Standard campaign — cost efficiency leads, volume and quality weighted equally.";
        };
    }

    public double weightOf(String factor) {
        return switch (factor) {
            case COST_EFFICIENCY -> costEfficiency;
            case VOLUME_FIT -> volumeFit;
            case QUALITY -> quality;
            case SPEED -> speed;
            case SKILL_MATCH -> skillMatch;
            default -> throw new IllegalArgumentException("Unknown factor: " + factor);
        };
    }

    public Map<String, Double> asMap() {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put(COST_EFFICIENCY, costEfficiency);
        weights.put(VOLUME_FIT, volumeFit);
        weights.put(QUALITY, quality);
        weights.put(SPEED, speed);
        weights.put(SKILL_MATCH, skillMatch);
        return weights;
    }
}
