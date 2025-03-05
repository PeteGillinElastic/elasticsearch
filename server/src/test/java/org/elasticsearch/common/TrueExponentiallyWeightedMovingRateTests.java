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
import static java.lang.Math.expm1;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.equalTo;

public class TrueExponentiallyWeightedMovingRateTests extends ESTestCase {

    private static final double TOLERANCE = 1.0e-14;

    public void testEwmr() {
        double lambda = 1.0e-6; // equivalent to half-life of log(2.0) * 1.0e6
        long startTimeInMillis = 1234567;
        TrueExponentiallyWeightedMovingRate ewmr = new TrueExponentiallyWeightedMovingRate(lambda, startTimeInMillis);
        assertThat(ewmr.getRate(startTimeInMillis), equalTo(0.0));
        assertThat(ewmr.getRate(startTimeInMillis + 900), equalTo(0.0));
        ewmr.addIncrement(10.0, startTimeInMillis + 1000);
        double expected1000 = lambda * 10.0 / (1.0 - exp(-1.0 * lambda * 1000)); // 0.010005... (~= 10 / 1000)
        assertThat(ewmr.getRate(startTimeInMillis + 1000), closeTo(expected1000, TOLERANCE));
        double expected1900 = lambda * 10.0 * exp(-1.0 * lambda * 900) / (1.0 - exp(-1.0 * lambda * 1900)); // 0.005263... (~= 10 / 1900)
        assertThat(ewmr.getRate(startTimeInMillis + 1900), closeTo(expected1900, TOLERANCE));
        ewmr.addIncrement(12.0, startTimeInMillis + 2000);
        ewmr.addIncrement(8.0, startTimeInMillis + 2500);
        double expected2500 = lambda * (8.0 + 12.0 * exp(-1.0 * lambda * 500) + 10.0 * exp(-1.0 * lambda * 1500)) / (1.0 - exp(
            -1.0 * lambda * 2500
        )); // 0.012006... (~= 30 / 2500)
        assertThat(ewmr.getRate(startTimeInMillis + 2500), closeTo(expected2500, TOLERANCE));
    }

    public void testEwmr_longSeriesEvenRate() {
        double lambda = 1.0e-6; // equivalent to half-life of log(2.0) * 1.0e6
        long startTimeInMillis = 1234567;
        long intervalMillis = 5000;
        double effectiveRate = 0.123;
        TrueExponentiallyWeightedMovingRate ewmr = new TrueExponentiallyWeightedMovingRate(lambda, startTimeInMillis);
        // Do loads of updates of size effectiveRate * intervalMillis ever intervalMillis:
        int numIncrements = 100_000;
        for (int i = 1; i <= numIncrements; i++) {
            ewmr.addIncrement(effectiveRate * intervalMillis, startTimeInMillis + intervalMillis * i);
        }
        // Expected rate is effectiveRate - use wider tolerance here as we don't expect the EWMR to match the effective rate super closely:
        assertThat(ewmr.getRate(startTimeInMillis + intervalMillis * numIncrements), closeTo(effectiveRate, 0.001));
    }

    public void testEwmr_longGapBetweenValues() {
        double lambda = 1.0e-6; // equivalent to half-life of log(2.0) * 1.0e6
        long startTimeInMillis = 1234567;
        TrueExponentiallyWeightedMovingRate ewmr = new TrueExponentiallyWeightedMovingRate(lambda, startTimeInMillis);
        assertThat(ewmr.getRate(startTimeInMillis), equalTo(0.0));
        assertThat(ewmr.getRate(startTimeInMillis + 900), equalTo(0.0));
        ewmr.addIncrement(10.0, startTimeInMillis + 1000);
        double expected1000 = lambda * 10.0 / (1.0 - exp(-1.0 * lambda * 1000)); // 0.010005... (~= 10 / 1000)
        assertThat(ewmr.getRate(startTimeInMillis + 1000), closeTo(expected1000, TOLERANCE));
        ewmr.addIncrement(20.0, startTimeInMillis + 2_000_000);
        double expected2000000 = lambda * (20.0 + 10.0 * exp(-1.0 * lambda * 1_999_000)) / (1.0 - exp(-1.0 * lambda * 2_000_000));
        // 0.000025... (more than 30 / 2000000 = 0.000015 because the gap is comparable to the half-life and we favour the recent increment)
        assertThat(ewmr.getRate(startTimeInMillis + 2_000_000), closeTo(expected2000000, TOLERANCE));
    }

    public void testEwmr_longGapBeforeFirstValue() {
        double lambda = 1.0e-6; // equivalent to half-life of log(2.0) * 1.0e6
        long startTimeInMillis = 1234567;
        TrueExponentiallyWeightedMovingRate ewmr = new TrueExponentiallyWeightedMovingRate(lambda, startTimeInMillis);
        assertThat(ewmr.getRate(startTimeInMillis), equalTo(0.0));
        assertThat(ewmr.getRate(startTimeInMillis + 1_900_000), equalTo(0.0));
        ewmr.addIncrement(10.0, startTimeInMillis + 2_000_000);
        double expected2000000 = lambda * 10.0 / (1.0 - exp(-1.0 * lambda * 2_000_000));
        // 0.000016... (more than 10 / 2000000 = 0.000005 because the gap is comparable to the half-life and we favour the recent increment)
        assertThat(ewmr.getRate(startTimeInMillis + 2_000_000), closeTo(expected2000000, TOLERANCE));
    }

    public void testEwmr_zeroLambda() {
        long startTimeInMillis = 1234567;
        TrueExponentiallyWeightedMovingRate ewmr = new TrueExponentiallyWeightedMovingRate(0.0, startTimeInMillis);
        assertThat(ewmr.getRate(startTimeInMillis), equalTo(0.0));
        assertThat(ewmr.getRate(startTimeInMillis + 900), equalTo(0.0));
        ewmr.addIncrement(10.0, startTimeInMillis + 1000);
        ewmr.addIncrement(20.0, startTimeInMillis + 1500);
        ewmr.addIncrement(15.0, startTimeInMillis + 2000);
        double expected2000 = (10.0 + 20.0 + 15.0) / 2000; // 0.0225
        assertThat(ewmr.getRate(startTimeInMillis + 2000), closeTo(expected2000, TOLERANCE));
        // Should still get unweighted cumulative rate even if we wait a long time before the next increment:
        ewmr.addIncrement(12.0, startTimeInMillis + 2_000_000);
        double expected2000000 = (10.0 + 20.0 + 15.0 + 12.0) / 2_000_000; // 0.0000285
        assertThat(ewmr.getRate(startTimeInMillis + 2_000_000), closeTo(expected2000000, TOLERANCE));
    }

    public void testEwmr_negativeZeroLambda() {
        long startTimeInMillis = 1234567;
        TrueExponentiallyWeightedMovingRate ewmr = new TrueExponentiallyWeightedMovingRate(-0.0, startTimeInMillis);
        assertThat(ewmr.getRate(startTimeInMillis), equalTo(0.0));
        assertThat(ewmr.getRate(startTimeInMillis + 900), equalTo(0.0));
        ewmr.addIncrement(10.0, startTimeInMillis + 1000);
        ewmr.addIncrement(20.0, startTimeInMillis + 1500);
        ewmr.addIncrement(15.0, startTimeInMillis + 2000);
        double expected2000 = (10.0 + 20.0 + 15.0) / 2000; // 0.0225
        assertThat(ewmr.getRate(startTimeInMillis + 2000), closeTo(expected2000, TOLERANCE));
        // Should still get unweighted cumulative rate even if we wait a long time before the next increment:
        ewmr.addIncrement(12.0, startTimeInMillis + 2_000_000);
        double expected2000000 = (10.0 + 20.0 + 15.0 + 12.0) / 2_000_000; // 0.0000285
        assertThat(ewmr.getRate(startTimeInMillis + 2_000_000), closeTo(expected2000000, TOLERANCE));
    }

    public void testEwmr_firstIncrementHappensImmediately() {
        double lambda = 1.0e-6; // equivalent to half-life of log(2.0) * 1.0e6
        long startTimeInMillis = 1234567;
        TrueExponentiallyWeightedMovingRate ewmr = new TrueExponentiallyWeightedMovingRate(lambda, startTimeInMillis);
        assertThat(ewmr.getRate(startTimeInMillis), equalTo(0.0));
        ewmr.addIncrement(10.0, startTimeInMillis);
        // The method contract states that we treat this as if the increment time was startTimeInMillis + 1:
        // N.B. We have to use expm1 here when calculating the expected value to avoid floating point error from 1.0 - exp(-1.0e-6).
        double expected = -1.0 * lambda * 10.0 / expm1(-1.0 * lambda * 1); // 10.000005... (~= 10 / 1)
        assertThat(ewmr.getRate(startTimeInMillis), closeTo(expected, TOLERANCE));
    }

    public void testEwmr_timeFlowsBackwardsBetweenIncrements() {
        double lambda = 1.0e-6; // equivalent to half-life of log(2.0) * 1.0e6
        long startTimeInMillis = 1234567;
        TrueExponentiallyWeightedMovingRate ewmr = new TrueExponentiallyWeightedMovingRate(lambda, startTimeInMillis);
        assertThat(ewmr.getRate(startTimeInMillis), equalTo(0.0));
        ewmr.addIncrement(10.0, startTimeInMillis + 1000);
        double expected1000 = lambda * 10.0 / (1.0 - exp(-1.0 * lambda * 1000)); // 0.010005... (~= 10 / 1000)
        assertThat(ewmr.getRate(startTimeInMillis + 1000), closeTo(expected1000, TOLERANCE));
        ewmr.addIncrement(20.0, startTimeInMillis + 900);
        // The method contract states that we treat this as if both increments happened at startTimeInMillis + 1000:
        double expected900 = lambda * (20.0 + 10.0) / (1.0 - exp(-1.0 * lambda * 1000)); // 0.030015 (~= 30 / 1000)
        assertThat(ewmr.getRate(startTimeInMillis + 900), closeTo(expected900, TOLERANCE));
    }

    public void testEwmr_askForRateAtTimeBeforeLastIncrement() {
        double lambda = 1.0e-6; // equivalent to half-life of log(2.0) * 1.0e6
        long startTimeInMillis = 1234567;
        TrueExponentiallyWeightedMovingRate ewmr = new TrueExponentiallyWeightedMovingRate(lambda, startTimeInMillis);
        assertThat(ewmr.getRate(startTimeInMillis), equalTo(0.0));
        ewmr.addIncrement(10.0, startTimeInMillis + 1000);
        double expected1000 = lambda * 10.0 / (1.0 - exp(-1.0 * lambda * 1000)); // 0.010005... (~= 10 / 1000)
        assertThat(ewmr.getRate(startTimeInMillis + 1000), closeTo(expected1000, TOLERANCE));
        ewmr.addIncrement(12.0, startTimeInMillis + 2000);
        ewmr.addIncrement(8.0, startTimeInMillis + 2500);
        double expected2500 = lambda * (8.0 + 12.0 * exp(-1.0 * lambda * 500) + 10.0 * exp(-1.0 * lambda * 1500)) / (1.0 - exp(
            -1.0 * lambda * 2500
        )); // 0.012006... (~= 30 / 2500)
        // The method contract states that, if we ask for the rate at a time before the last increment, we get the rate at the time of the
        // last increment instead:
        assertThat(ewmr.getRate(startTimeInMillis + 2400), closeTo(expected2500, TOLERANCE));
    }

    public void testEwmr_negativeLambdaThrowsOnConstruction() {
        long startTimeInMillis = 1234567;
        assertThrows(IllegalArgumentException.class, () -> new TrueExponentiallyWeightedMovingRate(-1.0e-6, startTimeInMillis));
    }

    public void testEwmr_zeroStartTimeInMillis() {
        double lambda = 1.0e-6; // equivalent to half-life of log(2.0) * 1.0e6
        assertThrows(IllegalArgumentException.class, () -> new TrueExponentiallyWeightedMovingRate(lambda, 0));
    }

    public void testEwmr_negativeStartTimeInMillis() {
        double lambda = 1.0e-6; // equivalent to half-life of log(2.0) * 1.0e6
        assertThrows(IllegalArgumentException.class, () -> new TrueExponentiallyWeightedMovingRate(lambda, -1));
    }
}
