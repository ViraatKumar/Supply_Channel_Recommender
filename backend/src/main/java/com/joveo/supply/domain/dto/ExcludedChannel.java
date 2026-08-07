package com.joveo.supply.domain.dto;

import java.util.List;

public record ExcludedChannel(
        String channelId,
        String channelName,
        List<RejectionReason> rejections
) {
    public record RejectionReason(String rule, String reason) {}
}
