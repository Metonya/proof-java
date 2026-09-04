package dev.proofjava.analysis.model;

/** Detector certainty, separate from severity (D-17). {@code LOW} is reserved by the schema; no v0.1 rule emits it. */
public enum Confidence {
    HIGH, MEDIUM, LOW, INCONCLUSIVE
}
