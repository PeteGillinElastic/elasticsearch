/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.common;

import static java.lang.Math.exp;

/**
 * Implements a version of an exponentially weighted moving rate (EWMR). This is a calculation over a finite time series of increments to
 * some sort of gauge which gives a value for the rate at which the gauge is being incremented where the weight given to an increment
 * decreases exponentially with how long ago it happened.
 *
 * <p><b>Warning</b>: This implementation is <b>not</b> thread-safe.
 */
public class TrueExponentiallyWeightedMovingRate {

    // TODO: Think about thread safety.
    // TODO: Think about numerical stability, especially in the case of small alpha, and especially especially in the case of alpha = 0.

    private final double lambda;
    private final long startTimeInMillis;
    private int count;
    private double rate;
    long lastTimeInMillis;

    /**
     * Constructs an instance with a given value of {@code lambda}, the parameter which dictates how quickly the average "forgets" older
     * increments. The weight given to an increment which happened a time {@code timeAgo} milliseconds ago will be proportional to
     * {@code exp(-1.0 * lambda * timeAgo)}. The half-life (measured in milliseconds) is related to this parameter by the equation
     * {@code exp(-1.0 * lambda * halfLife)} = 0.5}, so {@code lambda = log(2.0) / halfLife)}. The parameter {@code startTimeInMillis} gives
     * the time, in milliseconds since the epoch, to consider the start time for the rate calculation.
     */
    public TrueExponentiallyWeightedMovingRate(double lambda, long startTimeInMillis) {
        this.lambda = lambda;
        this.count = 0;
        this.rate = Double.NaN;
        this.startTimeInMillis = startTimeInMillis;
    }

    /**
     * Returns the EWMR as it would have been at the time of the last increment. If no increments have yet been added, returns
     * {@link Double#NaN}.
     */
    public double getRate() {
        return rate;
    }

    public void addIncrement(double increment, long timeInMillis) {
        if (count == 0) {
            rate = lambda * increment / (1.0 - exp(-1.0 * lambda * (timeInMillis - startTimeInMillis)));
        } else {
            rate = (lambda * increment + exp(-1.0 * lambda * (timeInMillis - startTimeInMillis)) * (exp(
                lambda * (lastTimeInMillis - startTimeInMillis)
            ) - 1.0) * rate) / (1.0 - exp(-1.0 * lambda * (timeInMillis - startTimeInMillis)));
        }
        count++;
        lastTimeInMillis = timeInMillis;
    }
}
