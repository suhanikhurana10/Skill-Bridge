package com.kaayakasetu.location;

/** Result of resolving a city name: coordinates + economic tier (Python returned a (lat, lon, tier) tuple). */
public record ResolvedCity(double lat, double lon, String tier) {}
