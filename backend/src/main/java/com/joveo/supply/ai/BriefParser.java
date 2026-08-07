package com.joveo.supply.ai;

import com.joveo.supply.ai.config.AiProperties;
import com.joveo.supply.ai.dto.BriefDraft;
import com.joveo.supply.ai.dto.BriefParseResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class BriefParser {

    private static final Logger log = LoggerFactory.getLogger(BriefParser.class);

    private static final String SYSTEM_PROMPT = """
            You extract structured campaign parameters from a recruiter's free-text hiring brief.

            Rules:
            - Only extract what the brief actually states or clearly implies. Never invent a
              number, a location, or a budget that is not there.
            - budget is a total campaign budget in INR. Convert shorthand: "5L"/"5 lakh" = 500000,
              "2cr"/"2 crore" = 20000000, "50k" = 50000.
            - timelineDays is the hiring window in days. Convert "3 weeks" to 21, "2 months" to 60.
            - seniority is exactly one of ENTRY, MID, SENIOR, EXECUTIVE, or null if not stated.
              Titles like "Lead", "Staff", "Principal" are SENIOR; "VP", "Head of", "Chief" and
              C-suite are EXECUTIVE.
            - skills are short lower_snake_case tags, e.g. engineering, sales, marketing, design,
              product, operations, finance, support, blue_collar.
            - remoteOk is true only if the brief says the role is remote or location-flexible.
            - Put anything else worth knowing (industry, shift pattern, visa needs) in
              additionalConstraints as a short phrase.
            - List the name of every field you had to leave null in missingFields.
            """;

    private final AiProperties properties;
    private final ChatClient chatClient;

    public BriefParser(AiProperties properties, ChatClient.Builder builder) {
        this.properties = properties;
        this.chatClient = properties.enabled() ? builder.build() : null;
        if (chatClient == null) {
            log.info("GEMINI_API_KEY not set — /api/parse-brief will report the parser as "
                    + "unavailable and the UI will use the manual form.");
        }
    }

    public BriefParseResponse parse(String brief) {
        if (chatClient == null) {
            return BriefParseResponse.unavailable(
                    "AI parsing is not configured on this server. Fill the form in manually.");
        }
        if (brief == null || brief.isBlank()) {
            return BriefParseResponse.unavailable("Paste a hiring brief first.");
        }

        String validatedBrief = validateBrief(brief);
        try {
            BriefDraft draft = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(validatedBrief)
                    .call()
                    .entity(BriefDraft.class);
            return BriefParseResponse.ok(draft);
        } catch (Exception e) {
            log.warn("Brief parsing failed ({}); falling back to the manual form.", e.toString());
            return BriefParseResponse.unavailable(
                    "Couldn't parse that automatically. Fill the form in manually.");
        }
    }
    private String validateBrief(String brief){
        // we can add more input clean-ups here if required
        return brief.length() > properties.maxBriefChars()
                ? brief.substring(0, properties.maxBriefChars())
                : brief;
    }
}
