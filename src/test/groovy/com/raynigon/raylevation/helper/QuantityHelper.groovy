package com.raynigon.raylevation.helper

import javax.measure.Quantity

class QuantityHelper {

    static def assertDelta(Quantity expected, Quantity actual, Double delta = 0.0001) {
        def expectedValue = expected.toSystemUnit().value.toDouble()
        def actualValue = actual.toSystemUnit().value.toDouble()
        return Math.abs(expectedValue - actualValue) <= delta
    }
}
