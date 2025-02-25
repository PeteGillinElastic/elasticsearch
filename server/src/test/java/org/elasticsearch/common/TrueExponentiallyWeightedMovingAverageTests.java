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

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;

public class TrueExponentiallyWeightedMovingAverageTests extends ESTestCase {

    private static final double TOLERANCE = 1.0e-10;

    public void testEWMA() {
        TrueExponentiallyWeightedMovingAverage ewma = new TrueExponentiallyWeightedMovingAverage(0.5);
        assertThat(ewma.getAverage(), equalTo(Double.NaN));
        ewma.addValue(10);
        assertThat(ewma.getAverage(), equalTo(10.0));
        ewma.addValue(12);
        // With alpha = 0.5, each weight should be half the previous.
        // So for two values, the weights should be 2/3 and 1/3:
        assertThat(ewma.getAverage(), closeTo((2.0 / 3) * 12 + (1.0 / 3) * 10, TOLERANCE));
        ewma.addValue(10);
        ewma.addValue(15);
        ewma.addValue(13);
        // For five values, the weights should be 16/31, 8/31, 4/31, 2/31, and 1/31:
        assertThat(
            ewma.getAverage(),
            closeTo((16.0 / 31) * 13 + (8.0 / 31) * 15 + (4.0 / 31) * 10 + (2.0 / 31) * 12 + (1.0 / 31) * 10, TOLERANCE)
        );
    }

    public void testConvergingToValue() {
        TrueExponentiallyWeightedMovingAverage ewma = new TrueExponentiallyWeightedMovingAverage(0.5);
        ewma.addValue(10000);
        for (int i = 0; i < 1000; i++) {
            ewma.addValue(1);
        }
        assertThat(ewma.getAverage(), lessThan(2.0));
        assertThat(ewma.getAverage(), greaterThanOrEqualTo(1.0));
    }

    public void testConvergingToValue_beatsStandardAlgorithm() {
        // Pick alpha = 0.1 here. The standard EWMA does worse for low alpha.
        TrueExponentiallyWeightedMovingAverage trueEwma = new TrueExponentiallyWeightedMovingAverage(0.1);
        trueEwma.addValue(10000);
        ExponentiallyWeightedMovingAverage standardEwma = new ExponentiallyWeightedMovingAverage(0.1, 10000);
        for (int i = 0; i < 250; i++) {
            trueEwma.addValue(1);
            standardEwma.addValue(1);
        }
        assertThat(trueEwma.getAverage(), lessThan(standardEwma.getAverage()));
    }
}
