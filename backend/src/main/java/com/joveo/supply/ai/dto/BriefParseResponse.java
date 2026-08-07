package com.joveo.supply.ai.dto;

public record BriefParseResponse(boolean parsed, BriefDraft draft, String message) {

    public static BriefParseResponse ok(BriefDraft draft) {
        return new BriefParseResponse(true, draft, null);
    }

    public static BriefParseResponse unavailable(String message) {
        return new BriefParseResponse(false, null, message);
    }
}
