package com.joveo.supply.domain.entity;

import com.joveo.supply.domain.dto.constant.ChannelType;
import com.joveo.supply.domain.StringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.List;
import java.util.Locale;

@Entity
@Table(name = "channel")
public class Channel {

    public static final String GLOBAL = "global";

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChannelType type;

    @Column(name = "cost_per_applicant", nullable = false)
    private double costPerApplicant;

    @Column(name = "min_budget", nullable = false)
    private double minBudget;

    @Column(name = "expected_volume_per_week", nullable = false)
    private double expectedVolumePerWeek;

    @Column(name = "quality_score", nullable = false)
    private double qualityScore;

    @Column(name = "lead_time_days", nullable = false)
    private int leadTimeDays;

    @Convert(converter = StringListConverter.class)
    @Column(name = "supported_locations", nullable = false, length = 1000)
    private List<String> supportedLocations;

    @Convert(converter = StringListConverter.class)
    @Column(name = "skill_tags", nullable = false, length = 1000)
    private List<String> skillTags;

    @Column(name = "channel_constraints", length = 500)
    private String constraints;

    protected Channel() {
    }

    public static Channel of(String id, String name, ChannelType type, double costPerApplicant,
                             double minBudget, double expectedVolumePerWeek, double qualityScore,
                             int leadTimeDays, List<String> supportedLocations,
                             List<String> skillTags, String constraints) {
        Channel channel = new Channel();
        channel.id = id;
        channel.name = name;
        channel.type = type;
        channel.costPerApplicant = costPerApplicant;
        channel.minBudget = minBudget;
        channel.expectedVolumePerWeek = expectedVolumePerWeek;
        channel.qualityScore = qualityScore;
        channel.leadTimeDays = leadTimeDays;
        channel.supportedLocations = List.copyOf(supportedLocations);
        channel.skillTags = List.copyOf(skillTags);
        channel.constraints = constraints;
        return channel;
    }

    public boolean isGlobal() {
        return supportedLocations.stream().anyMatch(GLOBAL::equalsIgnoreCase);
    }

    public boolean servesLocation(String location) {
        if (isGlobal()) {
            return true;
        }
        if (location == null || location.isBlank()) {
            return true;
        }
        String needle = location.toLowerCase(Locale.ROOT).trim();
        return supportedLocations.stream()
                .map(l -> l.toLowerCase(Locale.ROOT))
                .anyMatch(l -> l.contains(needle) || needle.contains(l));
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ChannelType getType() {
        return type;
    }

    public double getCostPerApplicant() {
        return costPerApplicant;
    }

    public double getMinBudget() {
        return minBudget;
    }

    public double getExpectedVolumePerWeek() {
        return expectedVolumePerWeek;
    }

    public double getQualityScore() {
        return qualityScore;
    }

    public int getLeadTimeDays() {
        return leadTimeDays;
    }

    public List<String> getSupportedLocations() {
        return supportedLocations;
    }

    public List<String> getSkillTags() {
        return skillTags;
    }

    public String getConstraints() {
        return constraints;
    }
}
