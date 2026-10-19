package com.kaayakasetu.feed;

import java.util.List;

/** Marketplace numbers shown in the dashboard's top ticker. */
public record PlatformInsights(
        long totalWorkers,
        long openJobs,
        long totalContacts,
        List<TradeStat> topTrades,
        List<CityStat> topCities) {

    public record TradeStat(String trade, long count, double avgRate) {}

    public record CityStat(String city, long count) {}
}
