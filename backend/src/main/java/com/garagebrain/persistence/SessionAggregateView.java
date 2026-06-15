package com.garagebrain.persistence;

public record SessionAggregateView(
        String segment, String pidName, double mean, double std, double min, double max, int n) {
}
