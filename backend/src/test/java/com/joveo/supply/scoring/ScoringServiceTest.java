package com.joveo.supply.scoring;

import com.joveo.supply.domain.dto.CampaignRequest;
import com.joveo.supply.domain.entity.Channel;
import com.joveo.supply.domain.dto.constant.ChannelType;
import com.joveo.supply.domain.dto.ExcludedChannel;
import com.joveo.supply.domain.dto.Recommendation;
import com.joveo.supply.domain.dto.RecommendationResponse;
import com.joveo.supply.domain.dto.constant.Seniority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringServiceTest {

    private final ScoringService scoring = new ScoringService();

    private static Channel channel(String id, double cost, double minBudget, double volumePerWeek,
                                   double quality, int leadTimeDays) {
        return Channel.of(id, id, ChannelType.JOB_BOARD, cost, minBudget, volumePerWeek, quality,
                leadTimeDays, List.of(Channel.GLOBAL), List.of("engineering"), null);
    }

    private static CampaignRequest request(int applicantsNeeded, double budget, int timelineDays) {
        return new CampaignRequest("Backend Engineer", "Bengaluru", applicantsNeeded, budget,
                timelineDays, List.of("engineering"), Seniority.MID, false, null, null);
    }

    private static Recommendation only(RecommendationResponse response) {
        assertThat(response.recommendations()).hasSize(1);
        return response.recommendations().get(0);
    }

    @Nested
    @DisplayName("Phase 1 — hard exclusion")
    class HardExclusion {

        @Test
        @DisplayName("a channel whose minimum spend exceeds the budget is excluded, not ranked low")
        void excludesUnaffordableMinimumSpend() {
            Channel expensive = channel("newsletter", 100, 40_000, 50, 8, 1);

            RecommendationResponse response =
                    scoring.recommend(List.of(expensive), request(10, 20_000, 30));

            assertThat(response.recommendations()).isEmpty();
            assertThat(response.excluded()).singleElement().satisfies(e -> {
                assertThat(e.channelId()).isEqualTo("newsletter");
                assertThat(e.rejections()).extracting(ExcludedChannel.RejectionReason::rule)
                        .containsExactly("MIN_BUDGET");
            });
        }

        @Test
        @DisplayName("a lead time equal to the timeline leaves no delivery window, so it is excluded")
        void excludesLeadTimeThatConsumesTheWholeTimeline() {
            Channel slow = channel("slow", 100, 0, 50, 8, 14);

            RecommendationResponse response =
                    scoring.recommend(List.of(slow), request(10, 100_000, 14));

            assertThat(response.recommendations()).isEmpty();
            assertThat(response.excluded()).singleElement().satisfies(e ->
                    assertThat(e.rejections()).extracting(ExcludedChannel.RejectionReason::rule)
                            .containsExactly("LEAD_TIME"));
        }

        @Test
        @DisplayName("a location-restricted channel is excluded, unless the role is remote")
        void excludesUnsupportedLocationUnlessRemote() {
            Channel indiaOnly = Channel.of("naukri", "Naukri", ChannelType.JOB_BOARD, 180, 0, 90, 6,
                    1, List.of("india", "bengaluru"), List.of("engineering"), null);
            CampaignRequest berlin = new CampaignRequest("Backend Engineer", "Berlin", 10, 100_000,
                    30, List.of("engineering"), Seniority.MID, false, null, null);

            RecommendationResponse onsite = scoring.recommend(List.of(indiaOnly), berlin);
            assertThat(onsite.excluded()).singleElement().satisfies(e ->
                    assertThat(e.rejections()).extracting(ExcludedChannel.RejectionReason::rule)
                            .containsExactly("LOCATION"));

            CampaignRequest remote = new CampaignRequest("Backend Engineer", "Berlin", 10, 100_000,
                    30, List.of("engineering"), Seniority.MID, true, null, null);

            RecommendationResponse anywhere = scoring.recommend(List.of(indiaOnly), remote);
            assertThat(anywhere.excluded()).isEmpty();
            assertThat(anywhere.recommendations()).hasSize(1);
        }

        @Test
        @DisplayName("a channel with no overlap with the required skills is excluded")
        void excludesChannelWithNoSkillOverlap() {
            Channel blueCollar = Channel.of("meta", "Meta Ads", ChannelType.SOCIAL, 70, 0, 220, 3,
                    2, List.of(Channel.GLOBAL), List.of("blue_collar", "support"), null);

            RecommendationResponse response =
                    scoring.recommend(List.of(blueCollar), request(10, 100_000, 30));

            assertThat(response.excluded()).singleElement().satisfies(e ->
                    assertThat(e.rejections()).extracting(ExcludedChannel.RejectionReason::rule)
                            .containsExactly("SKILL_OVERLAP"));
        }

        @Test
        @DisplayName("all failing rules are reported for a channel that fails multiple checks")
        void reportsAllFailingRules() {
            Channel doomed = channel("doomed", 100, 999_999, 50, 8, 60);

            RecommendationResponse response =
                    scoring.recommend(List.of(doomed), request(10, 20_000, 30));

            assertThat(response.excluded()).singleElement().satisfies(e ->
                    assertThat(e.rejections()).extracting(ExcludedChannel.RejectionReason::rule)
                            .containsExactlyInAnyOrder("MIN_BUDGET", "LEAD_TIME"));
        }
    }

    @Nested
    @DisplayName("Phase 2 — weighted scoring")
    class WeightedScoring {

        @Test
        @DisplayName("a channel that maxes every factor scores exactly 100, so the weights sum to 1")
        void perfectChannelScoresOneHundred() {
            Channel perfect = channel("perfect", 10, 0, 1_000, 10, 0);

            Recommendation top = only(scoring.recommend(List.of(perfect), request(10, 1_000, 14)));

            assertThat(top.score()).isEqualTo(100.0);
            assertThat(top.factorScores()).allSatisfy((factor, value) -> assertThat(value).isEqualTo(1.0));
        }

        @Test
        @DisplayName("over-delivery earns nothing extra — volume fit is capped at 1.0")
        void volumeFitIsCappedSoAFirehoseCannotDominate() {
            Channel enough = channel("enough", 100, 0, 10, 8, 0);
            Channel firehose = channel("firehose", 100, 0, 5_000, 8, 0);

            RecommendationResponse response =
                    scoring.recommend(List.of(enough, firehose), request(20, 100_000, 14));

            assertThat(response.recommendations())
                    .extracting(r -> r.factorScores().get(WeightProfile.VOLUME_FIT))
                    .containsExactly(1.0, 1.0);
            assertThat(response.recommendations().get(0).score())
                    .isEqualTo(response.recommendations().get(1).score());
        }

        @Test
        @DisplayName("cost efficiency still separates a cheap channel from a merely affordable one")
        void costEfficiencyDiscriminatesAboveFullCoverage() {
            Channel cheap = channel("cheap", 50, 0, 500, 8, 0);
            Channel affordable = channel("affordable", 900, 0, 500, 8, 0);

            RecommendationResponse response =
                    scoring.recommend(List.of(cheap, affordable), request(20, 20_000, 14));

            assertThat(response.recommendations().get(0).channelId()).isEqualTo("cheap");
            assertThat(response.recommendations().get(0).factorScores().get(WeightProfile.COST_EFFICIENCY))
                    .isGreaterThan(response.recommendations().get(1).factorScores().get(WeightProfile.COST_EFFICIENCY));
        }

        @Test
        @DisplayName("expected delivery is capped by the binding constraint, and says which one")
        void reportsTheBindingConstraint() {
            Channel pricey = channel("pricey", 500, 0, 100, 8, 0);
            Recommendation budgetBound = only(scoring.recommend(List.of(pricey), request(20, 3_000, 14)));
            assertThat(budgetBound.bindingConstraint()).isEqualTo("BUDGET");
            assertThat(budgetBound.expectedApplicants()).isEqualTo(6);
            assertThat(budgetBound.expectedCost()).isEqualTo(3_000);

            Channel trickle = channel("trickle", 10, 0, 2, 8, 0);
            Recommendation volumeBound = only(scoring.recommend(List.of(trickle), request(20, 100_000, 14)));
            assertThat(volumeBound.bindingConstraint()).isEqualTo("VOLUME");
            assertThat(volumeBound.expectedApplicants()).isEqualTo(4);

            Channel ample = channel("ample", 10, 0, 500, 8, 0);
            Recommendation demandBound = only(scoring.recommend(List.of(ample), request(20, 100_000, 14)));
            assertThat(demandBound.bindingConstraint()).isEqualTo("DEMAND");
            assertThat(demandBound.expectedApplicants()).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("Weight profiles")
    class Profiles {

        private final Channel bulk = channel("bulk", 50, 0, 200, 4, 0);
        private final Channel boutique = channel("boutique", 400, 0, 4, 10, 0);

        @Test
        @DisplayName("a mid-level search prefers the cheap high-volume channel")
        void balancedProfilePrefersReachAndPrice() {
            RecommendationResponse response =
                    scoring.recommend(List.of(bulk, boutique), request(20, 9_000, 28));

            assertThat(response.weightProfile()).isEqualTo(WeightProfile.BALANCED);
            assertThat(response.recommendations().get(0).channelId()).isEqualTo("bulk");
        }

        @Test
        @DisplayName("the same catalogue and budget flips for a senior search, on seniority alone")
        void seniorSearchSwitchesToQualityFirst() {
            CampaignRequest senior = new CampaignRequest("Principal Engineer", "Bengaluru", 20,
                    9_000, 28, List.of("engineering"), Seniority.SENIOR, false, null, null);

            RecommendationResponse response = scoring.recommend(List.of(bulk, boutique), senior);

            assertThat(response.weightProfile()).isEqualTo(WeightProfile.QUALITY_FIRST);
            assertThat(response.recommendations().get(0).channelId()).isEqualTo("boutique");
        }

        @Test
        @DisplayName("a bulk headcount switches to volume-first without any seniority signal")
        void bulkHeadcountSwitchesToVolumeFirst() {
            CampaignRequest bulkHiring = new CampaignRequest("Warehouse Associate", "Bengaluru",
                    WeightProfile.BULK_HIRING_THRESHOLD, 500_000, 28, List.of("engineering"),
                    Seniority.ENTRY, false, null, null);

            RecommendationResponse response = scoring.recommend(List.of(bulk, boutique), bulkHiring);

            assertThat(response.weightProfile()).isEqualTo(WeightProfile.VOLUME_FIRST);
            assertThat(response.weights()).containsEntry(WeightProfile.VOLUME_FIT, 0.40);
        }

        @Test
        @DisplayName("an explicit override beats the automatic choice")
        void overrideWins() {
            CampaignRequest forced = new CampaignRequest("Backend Engineer", "Bengaluru", 20, 9_000,
                    28, List.of("engineering"), Seniority.MID, false, null, WeightProfile.QUALITY_FIRST);

            RecommendationResponse response = scoring.recommend(List.of(bulk, boutique), forced);

            assertThat(response.weightProfile()).isEqualTo(WeightProfile.QUALITY_FIRST);
            assertThat(response.recommendations().get(0).channelId()).isEqualTo("boutique");
        }
    }

    @Nested
    @DisplayName("Determinism")
    class Determinism {

        @Test
        @DisplayName("a genuine tie is broken by quality, then price — never by catalogue order")
        void tiesBreakOnQualityThenPrice() {
            Channel alpha = channel("Alpha", 100, 0, 100, 8, 1);
            Channel beta = channel("Beta", 50, 0, 100, 8, 1);

            RecommendationResponse response =
                    scoring.recommend(List.of(alpha, beta), request(10, 100_000, 30));

            assertThat(response.recommendations()).extracting(Recommendation::score)
                    .containsExactly(response.recommendations().get(0).score(),
                            response.recommendations().get(0).score());
            assertThat(response.recommendations()).extracting(Recommendation::channelId)
                    .containsExactly("Beta", "Alpha");
            assertThat(response.recommendations()).extracting(Recommendation::rank)
                    .containsExactly(1, 2);
        }

        @Test
        @DisplayName("the same request against the same catalogue produces an identical ranking")
        void repeatedCallsAgree() {
            List<Channel> catalogue = List.of(
                    channel("a", 50, 0, 200, 4, 0),
                    channel("b", 400, 0, 40, 10, 2),
                    channel("c", 120, 0, 90, 7, 1));
            CampaignRequest campaign = request(30, 40_000, 21);

            RecommendationResponse first = scoring.recommend(catalogue, campaign);
            RecommendationResponse second = scoring.recommend(catalogue.reversed(), campaign);

            assertThat(second.recommendations()).usingRecursiveComparison()
                    .isEqualTo(first.recommendations());
        }
    }
}
