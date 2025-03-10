/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.benchmark.ewmr;

import org.elasticsearch.common.TrueExponentiallyWeightedMovingRate;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Fork(value = 3)
@Warmup(iterations = 2)
@Measurement(iterations = 3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class EWMRBenchmark {

    @Param({ "1600172297" })
    private long seed;

    @Param({ "1000000" })
    private int numValues;

    @Param({ "10" })
    private long intervalMillis;

    @Param({ "0.0", "1.0e-6" })
    private double lambda;

    private long startTimeMillis;
    private double[] values;
    private TrueExponentiallyWeightedMovingRate ewmr;
    private TrueExponentiallyWeightedMovingRate cmr;

    @Setup
    public void setUp() {
        this.startTimeMillis = System.currentTimeMillis();
        Random random = new Random(seed);
        this.values = random.doubles(numValues).toArray();
        this.ewmr = new TrueExponentiallyWeightedMovingRate(lambda, startTimeMillis);
        this.cmr = new TrueExponentiallyWeightedMovingRate(0.0, startTimeMillis);
    }

    @Benchmark
    public double benchmarkEwmrAddManyIncrementsThenGetRate() {
        for (int i = 0; i < numValues; i++) {
            ewmr.addIncrement(values[i], startTimeMillis + intervalMillis * (i + 1));
        }
        return ewmr.getRate(startTimeMillis + intervalMillis * numValues);
    }
}
