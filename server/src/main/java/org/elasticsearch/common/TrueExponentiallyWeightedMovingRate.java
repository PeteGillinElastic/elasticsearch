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
import static java.lang.Math.expm1;

/**
 * Implements a version of an exponentially weighted moving rate (EWMR). This is a calculation over a finite time series of increments to
 * some sort of gauge which gives a value for the rate at which the gauge is being incremented where the weight given to an increment
 * decreases exponentially with how long ago it happened.
 *
 * <p><b>Warning</b>: This implementation is <b>not</b> thread-safe.
 */
public class TrueExponentiallyWeightedMovingRate {

    // TODO: Think about thread safety.

    private final double lambda;
    private final long startTimeInMillis;
    private double rate;
    long lastTimeInMillis;

    /**
     * Constructor.
     *
     * @param lambda A parameter which dictates how quickly the average "forgets" older increments. The weight given to an increment which
     *     happened a time {@code timeAgo} milliseconds ago will be proportional to {@code exp(-1.0 * lambda * timeAgo)}. The half-life
     *     (measured in milliseconds) is related to this parameter by the equation {@code exp(-1.0 * lambda * halfLife)} = 0.5}, so
     *     {@code lambda = log(2.0) / halfLife)}. This may be zero, but must not be negative.
     * @param startTimeInMillis The time, in milliseconds since the epoch, to consider the start time for the rate calculation. This must be
     *     greater than zero.
     */
    public TrueExponentiallyWeightedMovingRate(double lambda, long startTimeInMillis) {
        if (lambda < 0.0) {
            throw new IllegalArgumentException("lambda must be non-negative but was " + lambda);
        }
        if (startTimeInMillis <= 0.0) {
            throw new IllegalArgumentException("lambda must be non-negative but was " + startTimeInMillis);
        }
        this.lambda = lambda;
        this.rate = Double.NaN; // should never be used
        this.startTimeInMillis = startTimeInMillis;
        this.lastTimeInMillis = 0; // after an increment, this must be positive, so a zero value indicates we're waiting for the first one
    }

    /**
     * Returns the EWMR at the given time, in millis since the epoch.
     *
     * <p>If there have been no increments yet, this returns zero.
     *
     * <p>Otherwise, we require the time to be no earlier than the time of the previous increment, i.e. the value of {@code timeInMillis}
     * for this call must not be less than the value of {@code timeInMillis} for the last call to {@link #addIncrement}. If this is not the
     * case, the method behaves as if it had that minimum value.
     */
    public double getRate(long timeInMillis) {
        if (lastTimeInMillis == 0) { // indicates that no increment has happened yet
            return 0.0;
        } else if (timeInMillis <= lastTimeInMillis) {
            return rate;
        } else {
            return expHelper(lambda, lastTimeInMillis - startTimeInMillis) * exp(-1.0 * lambda * (timeInMillis - lastTimeInMillis)) * rate
                / expHelper(lambda, timeInMillis - startTimeInMillis);
        }
    }

    /**
     * Updates the rate to reflect that the gauge has been incremented by an amount {@code increment} at a time {@code timeInMillis} in
     * milliseconds since the epoch.
     *
     * <p>If this is the first increment, we require it to occur after the start time for the rate calculation, i.e. the value of
     * {@code timeInMillis} must be greater than {@code startTimeInMillis} passed to the constructor. If this is not the case, the method
     * behaves as if {@code timeInMillis} is {@code startTimeInMillis + 1} to prevent a division by zero error.
     *
     * <p>If this is not the first increment, we require it not to occur before the previous increment, i.e. the value of
     * {@code timeInMillis} for this call must be greater than or equal to the value for the previous call. If this is not the case, the
     * method behaves as if this call's {@code timeInMillis} is the same as the previous call's.
     */
    public void addIncrement(double increment, long timeInMillis) {
        if (lastTimeInMillis == 0) { // indicates that this is the first increment
            if (timeInMillis <= startTimeInMillis) {
                timeInMillis = startTimeInMillis + 1;
            }
            rate = increment / expHelper(lambda, timeInMillis - startTimeInMillis);
        } else {
            if (timeInMillis < lastTimeInMillis) {
                timeInMillis = lastTimeInMillis;
            }
            rate += (increment - expHelper(lambda, timeInMillis - lastTimeInMillis) * rate) / expHelper(
                lambda,
                timeInMillis - startTimeInMillis
            );
        }
        lastTimeInMillis = timeInMillis;
    }

    /**
     * Returns something mathematically equivalent to {@code (1.0 - exp(-1.0 * lambda * time)) / lambda}, using an implementation which
     * should not be subject to numerical instability when {@code lambda * time} is small. Returns {@code time} when {@code lambda = 0},
     * which is the correct limit.
     */
    private double expHelper(double lambda, double time) {
        assert lambda >= 0.0;
        assert time >= 0.0;
        double lambdaTime = lambda * time;
        if (lambdaTime >= 1.0e-2) {
            // The direct calculation should be fine here:
            return (1.0 - exp(-1.0 * lambdaTime)) / lambda;
        } else if (lambdaTime >= 1.0e-10) {
            // Avoid taking the small difference of two similar quantities by using expm1 here:
            return -1.0 * expm1(-1.0 * lambdaTime) / lambda;
        } else {
            // Approximate exp(-1.0 * lambdaTime) = 1.0 - lambdaTime + 0.5 * lambdaTime * lambdaTime here (also works for lambda = 0):
            return time * (1.0 - 0.5 * lambdaTime);
        }
    }
}
