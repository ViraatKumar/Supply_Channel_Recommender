-- Eight channels, chosen so the scoring visibly discriminates rather than producing a
-- plausible-looking but flat ranking. The spread is deliberate:
--   high quality / low volume ......... Employee Referral, DevIndia Discord
--   cheap / high volume / low quality .. Indeed, Meta Ads
--   location restricted ................ Naukri (India only)
--   long lead time ..................... TechTalent Newsletter (10d), DevIndia Discord (7d)
--   high minimum spend ................. TechTalent Newsletter (₹40,000)
-- Costs are INR per applicant, order-of-magnitude realistic for the Indian market.

DELETE FROM channel;

INSERT INTO channel (id, name, type, cost_per_applicant, min_budget, expected_volume_per_week,
                     quality_score, lead_time_days, supported_locations, skill_tags, channel_constraints)
VALUES
('linkedin', 'LinkedIn Jobs', 'JOB_BOARD', 420, 25000, 35, 8, 2,
 'global',
 'engineering,product,design,sales,marketing,finance',
 'Weak for blue-collar and high-volume hourly roles; costs rise sharply in competitive metros.'),

('naukri', 'Naukri.com', 'JOB_BOARD', 180, 15000, 90, 6, 1,
 'india,bengaluru,mumbai,delhi,hyderabad,pune,chennai,remote-india',
 'engineering,sales,marketing,operations,finance,support',
 'India only. Deep for mid-market roles, thin for niche senior and international hiring.'),

('indeed', 'Indeed', 'AGGREGATOR', 95, 8000, 160, 4, 1,
 'global',
 'engineering,sales,operations,support,blue_collar,marketing',
 'Aggregated inbound with low intent — budget for roughly 3x the screening effort.'),

('wellfound', 'Wellfound (AngelList)', 'JOB_BOARD', 260, 12000, 22, 8, 3,
 'global',
 'engineering,product,design,marketing',
 'Startup-stage talent pool; poor fit for enterprise process roles or C-suite searches.'),

('devindia_discord', 'DevIndia Discord', 'COMMUNITY', 140, 5000, 12, 9, 7,
 'india,bengaluru,hyderabad,pune,remote-india',
 'engineering,design',
 'Engineering and design only, no C-suite. Needs a human in the community — a job dump gets ignored.'),

('techtalent_newsletter', 'TechTalent Newsletter', 'NEWSLETTER', 310, 40000, 18, 7, 10,
 'global',
 'engineering,product,design,marketing',
 'Fixed weekly sponsorship slots, booked 10 days ahead and non-refundable once placed.'),

('referral', 'Employee Referral Program', 'REFERRAL', 60, 2000, 6, 10, 0,
 'global',
 'engineering,product,design,sales,marketing,operations,finance,support',
 'Ceiling is your own headcount — reach does not scale with spend, and it narrows your funnel diversity.'),

('meta_ads', 'Meta Ads (Instagram/Facebook)', 'SOCIAL', 70, 10000, 220, 3, 2,
 'india,bengaluru,mumbai,delhi,hyderabad,pune,chennai',
 'blue_collar,operations,support,sales',
 'Interruption channel, not intent — very low relevance for specialised engineering roles.');
