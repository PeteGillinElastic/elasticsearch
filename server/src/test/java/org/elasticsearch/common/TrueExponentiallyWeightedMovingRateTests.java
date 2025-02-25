/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.common;

import org.elasticsearch.test.ESTestCase;

import static java.lang.Math.exp;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.equalTo;

public class TrueExponentiallyWeightedMovingRateTests extends ESTestCase {

    private static final double TOLERANCE = 1.0e-10;

    public void testEwmr() {
        double lambda = 1.0e-6; // equivalent to half-life of log(2.0) * 1.0e6
        long startTimeInMillis = 1234567;
        TrueExponentiallyWeightedMovingRate ewmr = new TrueExponentiallyWeightedMovingRate(lambda, startTimeInMillis);
        assertThat(ewmr.getRate(), equalTo(Double.NaN));
        ewmr.addIncrement(10.0, startTimeInMillis + 1000);
        assertThat(ewmr.getRate(), closeTo(lambda * 10.0 / (1.0 - exp(-1.0 * lambda * 1000)), TOLERANCE));
        // Expected rate is a fraction over 0.01 (increment 10 in 1000ms):
        assertThat(ewmr.getRate(), closeTo(0.01, 0.0001));
        ewmr.addIncrement(12.0, startTimeInMillis + 1200);
        ewmr.addIncrement(8.0, startTimeInMillis + 1500);
        assertThat(
            ewmr.getRate(),
            closeTo(
                lambda * (8.0 + 12.0 * exp(-1.0 * lambda * 300) + 10.0 * exp(-1.0 * lambda * 500)) / (1.0 - exp(-1.0 * lambda * 1500)),
                TOLERANCE
            )
        );
        // Expected rate is a fraction over 0.02 (increment 30 in 1500ms):
        assertThat(ewmr.getRate(), closeTo(0.02, 0.0001));
    }

    public void testEwmr_longSeriesEvenRate() {
        double lambda = 1.0e-6; // equivalent to half-life of log(2.0) * 1.0e6
        long startTimeInMillis = 1234567;
        long intervalMillis = 5000;
        double effectiveRate = 0.123;
        TrueExponentiallyWeightedMovingRate ewmr = new TrueExponentiallyWeightedMovingRate(lambda, startTimeInMillis);
        // Do loads of updates of size effectiveRate * intervalMillis ever intervalMillis:
        for (int i = 1; i <= 100_000; i++) {
            ewmr.addIncrement(effectiveRate * intervalMillis, startTimeInMillis + intervalMillis * i);
        }
        // Expected rate is effectiveRate - use wider tolerance here as we don't expect the EWMR to match the effective rate super closely:
        assertThat(ewmr.getRate(), closeTo(effectiveRate, 0.001));
    }
}
