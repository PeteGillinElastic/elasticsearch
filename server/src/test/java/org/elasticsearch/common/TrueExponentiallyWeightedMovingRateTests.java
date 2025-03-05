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
    private static final double HALF_LIFE_MILLIS = 1.0e6; // Half-life of used by many tests
    private static final double LAMBDA = Math.log(2.0) / HALF_LIFE_MILLIS; // Equivalent value of lambda
    public static final int START_TIME_IN_MILLIS = 1234567;

    public void testEwmr() {
        TrueExponentiallyWeightedMovingRate ewmr = new TrueExponentiallyWeightedMovingRate(LAMBDA, START_TIME_IN_MILLIS);
        assertThat(ewmr.getRate(START_TIME_IN_MILLIS), equalTo(0.0));
        assertThat(ewmr.getRate(START_TIME_IN_MILLIS + 900), equalTo(0.0));
        ewmr.addIncrement(10.0, START_TIME_IN_MILLIS + 1000);
        double expected1000 = LAMBDA * 10.0 / (1.0 - exp(-1.0 * LAMBDA * 1000));
        // 0.010003... (~= 10 / 1000 - greater than that, because an update just happened and we favour recent values - but only
        // fractionally, because the time interval is a small fraction of the half-life)
        assertThat(ewmr.getRate(START_TIME_IN_MILLIS + 1000), closeTo(expected1000, TOLERANCE));
        double expected1900 = LAMBDA * 10.0 * exp(-1.0 * LAMBDA * 900) / (1.0 - exp(-1.0 * LAMBDA * 1900)); // 0.005263... (~= 10 / 1900)
        assertThat(ewmr.getRate(START_TIME_IN_MILLIS + 1900), closeTo(expected1900, TOLERANCE));
        ewmr.addIncrement(12.0, START_TIME_IN_MILLIS + 2000);
        ewmr.addIncrement(8.0, START_TIME_IN_MILLIS + 2500);
        double expected2500 = LAMBDA * (8.0 + 12.0 * exp(-1.0 * LAMBDA * 500) + 10.0 * exp(-1.0 * LAMBDA * 1500)) / (1.0 - exp(
            -1.0 * LAMBDA * 2500
        )); // 0.012005... (~= 30 / 2500)
        assertThat(ewmr.getRate(START_TIME_IN_MILLIS + 2500), closeTo(expected2500, TOLERANCE));
    }

    public void testEwmr_zeroLambda() {
        TrueExponentiallyWeightedMovingRate ewmr = new TrueExponentiallyWeightedMovingRate(0.0, START_TIME_IN_MILLIS);
        ewmr.addIncrement(10.0, START_TIME_IN_MILLIS + 1000);
        ewmr.addIncrement(20.0, START_TIME_IN_MILLIS + 1500);
        ewmr.addIncrement(15.0, START_TIME_IN_MILLIS + 2000);
        double expected2000 = (10.0 + 20.0 + 15.0) / 2000; // 0.0225
        assertThat(ewmr.getRate(START_TIME_IN_MILLIS + 2000), closeTo(expected2000, TOLERANCE));
        // Should still get unweighted cumulative rate even if we wait a long time before the next increment:
        ewmr.addIncrement(12.0, START_TIME_IN_MILLIS + 2_000_000);
        double expected2000000 = (10.0 + 20.0 + 15.0 + 12.0) / 2_000_000; // 0.0000285
        assertThat(ewmr.getRate(START_TIME_IN_MILLIS + 2_000_000), closeTo(expected2000000, TOLERANCE));
    }

    public void testEwmr_longSeriesEvenRate() {
        TrueExponentiallyWeightedMovingRate ewmr = new TrueExponentiallyWeightedMovingRate(LAMBDA, START_TIME_IN_MILLIS);
        // Do loads of updates of size effectiveRate * intervalMillis ever intervalMillis:
        long intervalMillis = 5000;
        int numIncrements = 100_000;
        double effectiveRate = 0.123;
        for (int i = 1; i <= numIncrements; i++) {
            ewmr.addIncrement(effectiveRate * intervalMillis, START_TIME_IN_MILLIS + intervalMillis * i);
        }
        // Expected rate is roughly effectiveRate - use wider tolerance here as we this is an approximation:
        assertThat(ewmr.getRate(START_TIME_IN_MILLIS + intervalMillis * numIncrements), closeTo(effectiveRate, 0.001));
    }

    public void testEwmr_longSeriesWithStepChangeInRate_fitsHalfLife_contrastWithZeroLambda() {
        // In this test, we use a standard Exponentially Weighted Moving Rate...:
        TrueExponentiallyWeightedMovingRate ewmr = new TrueExponentiallyWeightedMovingRate(LAMBDA, START_TIME_IN_MILLIS);
        // ...and also one with zero lambda, giving an unweighted Cumulative Moving Rate:
        TrueExponentiallyWeightedMovingRate cmr = new TrueExponentiallyWeightedMovingRate(0.0, START_TIME_IN_MILLIS);
        long intervalMillis = 1000;

        // Phase 1: numIncrements1 increments at effective rate effectiveRate1 (increment size effectiveRate1 * intervalMillis):
        int numIncrements1 = 90_000; // 90_000 increments at intervals of 1000ms take 100_000_000ms, or 90 half-lives
        double effectiveRate1 = 0.123;
        for (int i = 1; i <= numIncrements1; i++) {
            ewmr.addIncrement(effectiveRate1 * intervalMillis, START_TIME_IN_MILLIS + intervalMillis * i);
            cmr.addIncrement(effectiveRate1 * intervalMillis, START_TIME_IN_MILLIS + intervalMillis * i);
        }
        // Expected rate for both EWMR and CMR is roughly effectiveRate1 - use wider tolerance here as we this is an approximation:
        assertThat(ewmr.getRate(START_TIME_IN_MILLIS + intervalMillis * numIncrements1), closeTo(effectiveRate1, 0.001));
        assertThat(cmr.getRate(START_TIME_IN_MILLIS + intervalMillis * numIncrements1), closeTo(effectiveRate1, 0.001));

        // Phase 2a: numIncrements2a increments at effective rate effectiveRate2 (increment size effectiveRate2 * intervalMillis):
        long phase2aStartTimeMillis = START_TIME_IN_MILLIS + intervalMillis * numIncrements1;
        int numIncrements2a = 1000; // 1000 increments at intervals of 1000ms take 1_000_000ms, or exactly one half-life
        double effectiveRate2 = 0.345;
        // Do the first increment at the higher rate:
        ewmr.addIncrement(effectiveRate2 * intervalMillis, phase2aStartTimeMillis + intervalMillis);
        cmr.addIncrement(effectiveRate2 * intervalMillis, phase2aStartTimeMillis + intervalMillis);
        // That first increment at the higher rate shouldn't make much difference to the EWMR, we still expect roughly effectiveRate1:
        assertThat(ewmr.getRate(phase2aStartTimeMillis + intervalMillis), closeTo(effectiveRate1, 0.001));
        // Now do the rest of the 1000 increments:
        for (int i = 2; i <= numIncrements2a; i++) {
            ewmr.addIncrement(effectiveRate2 * intervalMillis, phase2aStartTimeMillis + intervalMillis * i);
            cmr.addIncrement(effectiveRate2 * intervalMillis, phase2aStartTimeMillis + intervalMillis * i);
        }
        // Since we have been at effectiveRate2 for one half-life, and we were at effectiveRate1 for 90 half-lives (which is effectively
        // forever) before that, we expect the EWMR to be roughly halfway between the two rates:
        double expectedEwmr2a = 0.5 * (effectiveRate1 + effectiveRate2); // 0.5 * (0.123 + 0.345) = 0.234
        assertThat(ewmr.getRate(phase2aStartTimeMillis + intervalMillis * numIncrements2a), closeTo(expectedEwmr2a, 0.001));

        // Phase 2b: numIncrements2b increments at effective rate effectiveRate2 (increment size effectiveRate2 * intervalMillis):
        long phase2bStartTimeMillis = phase2aStartTimeMillis + intervalMillis * numIncrements2a;
        int numIncrements2b = 9000; // 9000 increments at intervals of 1000ms take 9_000_000ms, or 9 half-lives
        for (int i = 1; i <= numIncrements2b; i++) {
            ewmr.addIncrement(effectiveRate2 * intervalMillis, phase2bStartTimeMillis + intervalMillis * i);
            cmr.addIncrement(effectiveRate2 * intervalMillis, phase2bStartTimeMillis + intervalMillis * i);
        }
        // Since we have been at effectiveRate2 for 10 half-lives (which is effectively forever) across phases 2a and 2b, that's now the
        // approximate expected EWMR:
        assertThat(ewmr.getRate(phase2bStartTimeMillis + intervalMillis * numIncrements2b), closeTo(effectiveRate2, 0.001));
        // We did 90_000 increments at effectiveRate1 and 10_000 at effectiveRate2, and for the CMR each increment is equally weighted, so
        // we expect the CMR to place 9x the weight on effectiveRate1 relative to effectiveRate2:
        double expectedCmr2b = 0.9 * effectiveRate1 + 0.1 * effectiveRate2; // 0.9 * 0.123 + 0.1 * 0.345 = 0.1452
        assertThat(cmr.getRate(phase2bStartTimeMillis + intervalMillis * numIncrements2b), closeTo(expectedCmr2b, 0.001));
        // N.B. At this point the EWMR is dominated by the new rate, while the CMR is still largely dominated by the old rate.
    }

    public void testEwmr_longGapBetweenValues_higherRecentValue() {
        TrueExponentiallyWeightedMovingRate ewmr = new TrueExponentiallyWeightedMovingRate(LAMBDA, START_TIME_IN_MILLIS);
        ewmr.addIncrement(10.0, START_TIME_IN_MILLIS + 1000);
        ewmr.addIncrement(30.0, START_TIME_IN_MILLIS + 2_000_000);
        double expected2000000 = LAMBDA * (30.0 + 10.0 * exp(-1.0 * LAMBDA * 1_999_000)) / (1.0 - exp(-1.0 * LAMBDA * 2_000_000));
        // 0.000030... (more than 40 / 2000000 = 0.000020 because the gap is twice the half-life and we favour the larger recent increment)
        assertThat(ewmr.getRate(START_TIME_IN_MILLIS + 2_000_000), closeTo(expected2000000, TOLERANCE));
    }

    public void testEwmr_longGapBetweenValues_lowerRecentValue() {
        TrueExponentiallyWeightedMovingRate ewmr = new TrueExponentiallyWeightedMovingRate(LAMBDA, START_TIME_IN_MILLIS);
        ewmr.addIncrement(30.0, START_TIME_IN_MILLIS + 1000);
        ewmr.addIncrement(10.0, START_TIME_IN_MILLIS + 2_000_000);
        double expected2000000 = LAMBDA * (10.0 + 30.0 * exp(-1.0 * LAMBDA * 1_999_000)) / (1.0 - exp(-1.0 * LAMBDA * 2_000_000));
        // 0.000016... (less than 40 / 2000000 = 0.000020 because the gap is twice the half-life and we favour the smaller recent increment)
        assertThat(ewmr.getRate(START_TIME_IN_MILLIS + 2_000_000), closeTo(expected2000000, TOLERANCE));
    }

    public void testEwmr_longGapBeforeFirstValue() {
        TrueExponentiallyWeightedMovingRate ewmr = new TrueExponentiallyWeightedMovingRate(LAMBDA, START_TIME_IN_MILLIS);
        ewmr.addIncrement(10.0, START_TIME_IN_MILLIS + 2_000_000);
        double expected2000000 = LAMBDA * 10.0 / (1.0 - exp(-1.0 * LAMBDA * 2_000_000));
        // 0.000009... (more than 10 / 2000000 = 0.000005 because the gap twice the half-life and we favour the recent increment)
        assertThat(ewmr.getRate(START_TIME_IN_MILLIS + 2_000_000), closeTo(expected2000000, TOLERANCE));
    }

    public void testEwmr_longGapAfterLastIncrement() {
        TrueExponentiallyWeightedMovingRate ewmr = new TrueExponentiallyWeightedMovingRate(LAMBDA, START_TIME_IN_MILLIS);
        ewmr.addIncrement(10.0, START_TIME_IN_MILLIS + 1000);
        ewmr.addIncrement(12.0, START_TIME_IN_MILLIS + 2000);
        ewmr.addIncrement(8.0, START_TIME_IN_MILLIS + 2500);
        double expected2000000 = LAMBDA * (8.0 * exp(-1.0 * LAMBDA * 1_997_500) + 12.0 * exp(-1.0 * LAMBDA * 1_998_000) + 10.0 * exp(
            -1.0 * LAMBDA * 1_999_000
        )) / (1.0 - exp(-1.0 * LAMBDA * 2_000_000));
        // 0.000007... (less than 30 / 2000000 = 0.000015 because the updates were nearly two half-lives ago)
        assertThat(ewmr.getRate(START_TIME_IN_MILLIS + 2_000_000), closeTo(expected2000000, TOLERANCE));
    }

    public void testEwmr_firstIncrementHappensImmediately() {
        TrueExponentiallyWeightedMovingRate ewmr = new TrueExponentiallyWeightedMovingRate(LAMBDA, START_TIME_IN_MILLIS);
        ewmr.addIncrement(10.0, START_TIME_IN_MILLIS);
        // The method contract states that we treat this as if the increment time was START_TIME_IN_MILLIS + 1:
        // N.B. We have to use expm1 here when calculating the expected value to avoid floating point error from 1.0 - exp(-1.0e-6).
        double expected = -1.0 * LAMBDA * 10.0 / expm1(-1.0 * LAMBDA * 1); // 10.000003... (~= 10 / 1)
        assertThat(ewmr.getRate(START_TIME_IN_MILLIS), closeTo(expected, TOLERANCE));
    }

    public void testEwmr_timeFlowsBackwardsBetweenIncrements() {
        TrueExponentiallyWeightedMovingRate ewmr = new TrueExponentiallyWeightedMovingRate(LAMBDA, START_TIME_IN_MILLIS);
        ewmr.addIncrement(10.0, START_TIME_IN_MILLIS + 1000);
        ewmr.addIncrement(20.0, START_TIME_IN_MILLIS + 900);
        // The method contract states that we treat this as if both increments happened at START_TIME_IN_MILLIS + 1000:
        double expected900 = LAMBDA * (20.0 + 10.0) / (1.0 - exp(-1.0 * LAMBDA * 1000)); // 0.030010 (~= 30 / 1000)
        assertThat(ewmr.getRate(START_TIME_IN_MILLIS + 900), closeTo(expected900, TOLERANCE));
    }

    public void testEwmr_askForRateAtTimeBeforeLastIncrement() {
        TrueExponentiallyWeightedMovingRate ewmr = new TrueExponentiallyWeightedMovingRate(LAMBDA, START_TIME_IN_MILLIS);
        ewmr.addIncrement(10.0, START_TIME_IN_MILLIS + 1000);
        ewmr.addIncrement(12.0, START_TIME_IN_MILLIS + 2000);
        ewmr.addIncrement(8.0, START_TIME_IN_MILLIS + 2500);
        double expected2500 = LAMBDA * (8.0 + 12.0 * exp(-1.0 * LAMBDA * 500) + 10.0 * exp(-1.0 * LAMBDA * 1500)) / (1.0 - exp(
            -1.0 * LAMBDA * 2500
        )); // 0.012005... (~= 30 / 2500)
        // The method contract states that, if we ask for the rate at a time before the last increment, we get the rate at the time of the
        // last increment instead:
        assertThat(ewmr.getRate(START_TIME_IN_MILLIS + 2400), closeTo(expected2500, TOLERANCE));
    }

    public void testEwmr_negativeLambdaThrowsOnConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new TrueExponentiallyWeightedMovingRate(-1.0e-6, START_TIME_IN_MILLIS));
    }

    public void testEwmr_zeroStartTimeInMillis() {
        assertThrows(IllegalArgumentException.class, () -> new TrueExponentiallyWeightedMovingRate(LAMBDA, 0));
    }

    public void testEwmr_negativeStartTimeInMillis() {
        assertThrows(IllegalArgumentException.class, () -> new TrueExponentiallyWeightedMovingRate(LAMBDA, -1));
    }
}
