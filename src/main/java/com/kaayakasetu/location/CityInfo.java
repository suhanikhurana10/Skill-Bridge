package com.kaayakasetu.location;

/** One entry of the built-in city table (JSON: name, state, lat, lon, tier, primary_lang). */
public record CityInfo(String name, String state, double lat, double lon, String tier, String primaryLang) {}
