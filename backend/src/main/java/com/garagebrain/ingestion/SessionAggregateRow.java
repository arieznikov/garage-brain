package com.garagebrain.ingestion;

public record SessionAggregateRow(
        String segment, String pidName, double mean, double std, double min, double max, int n) {
}
