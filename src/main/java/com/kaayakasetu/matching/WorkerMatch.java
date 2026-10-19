package com.kaayakasetu.matching;

import com.kaayakasetu.worker.Worker;

/** A worker ranked for a given job. */
public record WorkerMatch(Worker worker, double matchScore, MatchScoreBreakdown breakdown) {}
