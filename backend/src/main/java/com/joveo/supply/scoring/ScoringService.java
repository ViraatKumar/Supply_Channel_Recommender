package com.joveo.supply.scoring;

import com.joveo.supply.domain.dto.CampaignRequest;
import com.joveo.supply.domain.entity.Channel;
import com.joveo.supply.domain.dto.ExcludedChannel;
import com.joveo.supply.domain.dto.Recommendation;
import com.joveo.supply.domain.dto.RecommendationResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.joveo.supply.scoring.WeightProfile.COST_EFFICIENCY;
import static com.joveo.supply.scoring.WeightProfile.QUALITY;
import static com.joveo.supply.scoring.WeightProfile.SKILL_MATCH;
import static com.joveo.supply.scoring.WeightProfile.SPEED;
import static com.joveo.supply.scoring.WeightProfile.VOLUME_FIT;

@Service
public class ScoringService {

    private static final double FULL_MARKS_COVERAGE = 2.0;
    private static final double UNDER_COVERAGE_CEILING = 0.6;
    private static final double WEAK_FACTOR = 0.4;

    public RecommendationResponse recommend(List<Channel> channels, CampaignRequest request) {
        WeightProfile profile = WeightProfile.select(request);

        List<ExcludedChannel> excluded = new ArrayList<>();
        List<Scored> scored = new ArrayList<>();

        for (Channel channel : channels) {
            ExcludedChannel rejection = exclude(channel, request);
            if (rejection != null) {
                excluded.add(rejection);
            } else {
                scored.add(score(channel, request, profile));
            }
        }

        scored.sort(Comparator.comparingDouble(Scored::score).reversed()
                .thenComparing(Comparator.comparingDouble((Scored s) -> s.channel().getQualityScore()).reversed())
                .thenComparingDouble(s -> s.channel().getCostPerApplicant())
                .thenComparing(s -> s.channel().getName()));

        List<Recommendation> recommendations = new ArrayList<>(scored.size());
        for (int i = 0; i < scored.size(); i++) {
            recommendations.add(scored.get(i).toRecommendation(i + 1, request, profile));
        }
        excluded.sort(Comparator.comparing(ExcludedChannel::rule).thenComparing(ExcludedChannel::channelName));

        return new RecommendationResponse(recommendations, excluded, profile, profile.asMap(),
                WeightProfile.rationaleFor(request, profile));
    }

    private static ExcludedChannel exclude(Channel channel, CampaignRequest request) {
        if (channel.getMinBudget() > request.budget()) {
            return reject(channel, "MIN_BUDGET", "Minimum spend of %s exceeds the campaign budget of %s."
                    .formatted(inr(channel.getMinBudget()), inr(request.budget())));
        }
        if (channel.getLeadTimeDays() >= request.timelineDays()) {
            return reject(channel, "LEAD_TIME", "Lead time of %d days leaves no delivery window inside a %d-day timeline."
                    .formatted(channel.getLeadTimeDays(), request.timelineDays()));
        }
        if (!request.remoteOk() && !channel.servesLocation(request.location())) {
            return reject(channel, "LOCATION", "Does not supply applicants in %s (covers: %s)."
                    .formatted(request.location(), String.join(", ", channel.getSupportedLocations())));
        }
        if (!request.skills().isEmpty() && request.skills().stream().noneMatch(s -> hasTag(channel, s))) {
            return reject(channel, "SKILL_OVERLAP", "No overlap with the required skills (%s); this channel supplies %s."
                    .formatted(String.join(", ", request.skills()), String.join(", ", channel.getSkillTags())));
        }
        return null;
    }

    private static ExcludedChannel reject(Channel channel, String rule, String reason) {
        return new ExcludedChannel(channel.getId(), channel.getName(), rule, reason);
    }

    private static Scored score(Channel channel, CampaignRequest request, WeightProfile profile) {
        Delivery delivery = estimate(channel, request);
        double needed = request.applicantsNeeded();

        Map<String, Double> factors = new LinkedHashMap<>();
        factors.put(COST_EFFICIENCY, costEfficiency(delivery.affordable() / needed));
        factors.put(VOLUME_FIT, clamp01(delivery.reachable() / needed));
        factors.put(QUALITY, clamp01(channel.getQualityScore() / 10.0));
        factors.put(SPEED, clamp01((request.timelineDays() - channel.getLeadTimeDays()) / (double) request.timelineDays()));
        factors.put(SKILL_MATCH, skillMatch(channel, request));

        double weighted = 0;
        for (Map.Entry<String, Double> factor : factors.entrySet()) {
            weighted += factor.getValue() * profile.weightOf(factor.getKey());
        }

        factors.replaceAll((name, value) -> Math.round(value * 1000.0) / 1000.0);
        return new Scored(channel, delivery, factors, Math.round(weighted * 1000.0) / 10.0);
    }

    private static double costEfficiency(double coverage) {
        if (coverage <= 0) {
            return 0.0;
        }
        if (coverage < 1.0) {
            return UNDER_COVERAGE_CEILING * coverage;
        }
        double headroom = Math.min(1.0, (coverage - 1.0) / (FULL_MARKS_COVERAGE - 1.0));
        return UNDER_COVERAGE_CEILING + (1.0 - UNDER_COVERAGE_CEILING) * headroom;
    }

    private static double skillMatch(Channel channel, CampaignRequest request) {
        if (request.skills().isEmpty()) {
            return 1.0;
        }
        long matched = request.skills().stream().filter(s -> hasTag(channel, s)).count();
        return clamp01((double) matched / request.skills().size());
    }

    private record Delivery(int deliverableDays, double reachable, double affordable,
                            int expectedApplicants, long expectedCost, String bindingConstraint) {
    }

    private static Delivery estimate(Channel channel, CampaignRequest request) {
        int deliverableDays = Math.max(0, request.timelineDays() - channel.getLeadTimeDays());
        double reachable = channel.getExpectedVolumePerWeek() * (deliverableDays / 7.0);
        double affordable = channel.getCostPerApplicant() <= 0
                ? Double.MAX_VALUE
                : request.budget() / channel.getCostPerApplicant();

        double needed = request.applicantsNeeded();
        double capped = Math.min(needed, Math.min(reachable, affordable));
        int expected = (int) Math.floor(Math.max(0, capped));

        String binding = capped == needed ? "DEMAND" : reachable <= affordable ? "VOLUME" : "BUDGET";
        long cost = Math.round(Math.min(request.budget(), expected * channel.getCostPerApplicant()));

        return new Delivery(deliverableDays, reachable, affordable, expected, cost, binding);
    }

    private record Scored(Channel channel, Delivery delivery, Map<String, Double> factors, double score) {

        Recommendation toRecommendation(int rank, CampaignRequest request, WeightProfile profile) {
            return new Recommendation(channel.getId(), channel.getName(), channel.getType(), rank, score,
                    factors, delivery.expectedCost(), delivery.expectedApplicants(),
                    channel.getCostPerApplicant(), channel.getQualityScore(), channel.getLeadTimeDays(),
                    delivery.bindingConstraint(), reason(this, profile), limitations(this, request));
        }
    }

    private static String reason(Scored s, WeightProfile profile) {
        Channel channel = s.channel();
        String top = s.factors().entrySet().stream()
                .max(Comparator.comparingDouble(e -> e.getValue() * profile.weightOf(e.getKey())))
                .map(Map.Entry::getKey)
                .orElse(QUALITY);

        String lead = switch (top) {
            case COST_EFFICIENCY -> "Cost efficiency leads at %s per applicant".formatted(inr(channel.getCostPerApplicant()));
            case VOLUME_FIT -> "Reach leads at %.0f applicants a week".formatted(channel.getExpectedVolumePerWeek());
            case QUALITY -> "Applicant relevance leads at %.0f/10".formatted(channel.getQualityScore());
            case SPEED -> "Speed leads — live in %d day%s".formatted(channel.getLeadTimeDays(),
                    channel.getLeadTimeDays() == 1 ? "" : "s");
            default -> "Skill fit leads (%s)".formatted(String.join(", ", channel.getSkillTags()));
        };

        return "%s. Expect %d applicants for %s over %d days."
                .formatted(lead, s.delivery().expectedApplicants(), inr(s.delivery().expectedCost()),
                        s.delivery().deliverableDays());
    }

    private static List<String> limitations(Scored s, CampaignRequest request) {
        Channel channel = s.channel();
        List<String> limitations = new ArrayList<>();

        if (channel.getConstraints() != null && !channel.getConstraints().isBlank()) {
            limitations.add(channel.getConstraints());
        }
        if (!"DEMAND".equals(s.delivery().bindingConstraint())) {
            limitations.add("%s caps this at %d applicants — %d short of your target.".formatted(
                    "BUDGET".equals(s.delivery().bindingConstraint()) ? "Budget" : "Throughput",
                    s.delivery().expectedApplicants(),
                    request.applicantsNeeded() - s.delivery().expectedApplicants()));
        }
        if (s.factors().get(QUALITY) <= WEAK_FACTOR) {
            limitations.add("Applicant relevance is %.0f/10 — expect a heavy screening load."
                    .formatted(channel.getQualityScore()));
        }
        if (s.factors().get(SPEED) <= WEAK_FACTOR) {
            limitations.add("Setup takes %d of your %d days before the first applicant."
                    .formatted(channel.getLeadTimeDays(), request.timelineDays()));
        }
        return limitations;
    }

    private static boolean hasTag(Channel channel, String skill) {
        String needle = skill.toLowerCase(Locale.ROOT).trim();
        return channel.getSkillTags().stream()
                .map(t -> t.toLowerCase(Locale.ROOT))
                .anyMatch(t -> t.contains(needle) || needle.contains(t));
    }

    private static double clamp01(double value) {
        return Double.isNaN(value) ? 0.0 : Math.max(0.0, Math.min(1.0, value));
    }

    private static String inr(double amount) {
        return "₹" + String.format("%,.0f", amount);
    }
}
