package com.kaayakasetu.matching;

import com.kaayakasetu.job.Job;

/** A job ranked for a given worker. */
public record JobMatch(Job job, double matchScore, MatchScoreBreakdown breakdown) {}
