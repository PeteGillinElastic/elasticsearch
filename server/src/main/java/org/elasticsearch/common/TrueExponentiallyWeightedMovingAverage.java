/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.common;

import static java.lang.Math.pow;

/**
 * Implements an alternative version of an exponentially weighted moving average (EWMA) to that implemented by
 * {@link ExponentiallyWeightedMovingAverage}. That one takes an initial value. Either the code using it must pick an arbitrary value, or
 * it must delay construction of the instance until the first actual value appears. What's more, even if the first actual value is used,
 * it tends to over-weight that first value, and to keep over-weighing it until enough new values has been added. This over-weighting is
 * more pronounced and more persistent for smaller values of {@code alpha}.
 *
 * <p><b>Warning</b>: This implementation is <b>not</b> thread-safe.
 */
public class TrueExponentiallyWeightedMovingAverage {

    // TODO: Think about thread safety.
    // TODO: Think about numerical stability, especially in the case of small alpha, and especially especially in the case of alpha = 0.

    private final double alpha;
    private int count;
    private double average;

    /**
     * Constructs an instance with a given value of {@code alpha}, the parameter which dictates how quickly the average "forgets" older
     * values. The weight given to each value will be smaller than the weight given to the value which comes after it, by a factor of
     * {@code 1.0 - alpha}. The half-life (measured in values) is related to this parameter by the equation
     * {@code pow(1.0 - alpha, halfLife} = 0.5}, so {@code alpha = 1.0 - pow(2.0, -1.0 / halfLife)}.
     */
    public TrueExponentiallyWeightedMovingAverage(double alpha) {
        this.alpha = alpha;
        this.count = 0;
        this.average = Double.NaN;
    }

    /**
     * Returns the current EWMA. If no values have yet been added, returns {@link Double#NaN}.
     */
    public double getAverage() {
        return average;
    }

    public void addValue(double newValue) {
        if (count == 0) {
            average = newValue;
        } else {
            average = (alpha * newValue + (1.0 - alpha) * (1.0 - pow(1.0 - alpha, count)) * average) / (1.0 - pow(1.0 - alpha, count + 1));
        }
        count++;
    }
}
