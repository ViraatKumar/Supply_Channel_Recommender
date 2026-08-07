package com.joveo.supply.domain.dto;

public record ExcludedChannel(
        String channelId,
        String channelName,
        String rule,
        String reason
) {
}
