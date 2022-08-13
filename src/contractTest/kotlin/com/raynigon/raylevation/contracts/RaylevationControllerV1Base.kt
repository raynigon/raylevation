package com.raynigon.raylevation.contracts

import com.raynigon.raylevation.base.controller.RaylevationController
import com.raynigon.raylevation.base.controller.RaylevationControllerAdvisor
import com.raynigon.raylevation.base.service.LocationsParserImpl
import com.raynigon.raylevation.base.service.LookupResult
import com.raynigon.raylevation.base.service.RaylevationService
import com.raynigon.raylevation.infrastructure.model.GeoPoint
import com.raynigon.unit.api.core.units.si.SISystemUnitsConstants.Metre
import io.mockk.every
import io.mockk.mockk

/**
 * Base Class for [RaylevationController] Tests
 */
abstract class RaylevationControllerV1Base : AbstractStandaloneMvcTest<RaylevationController>() {

    private val service = mockk<RaylevationService>().also { service ->
        every { service.lookup(emptyList()) } returns listOf()
        every { service.lookup(match { it.size == 3 }) } returns listOf(
            LookupResult(GeoPoint(10.0, 10.0), Metre(515)),
            LookupResult(GeoPoint(20.0, 20.0), Metre(545)),
            LookupResult(GeoPoint(41.161758, -8.583933), Metre(117)),
        )
        every { service.lookup(match { it.size == 4 }) } returns listOf(
            LookupResult(GeoPoint(10.0, 10.0), Metre(515)),
            LookupResult(GeoPoint(20.0, 20.0), Metre(545)),
            LookupResult(GeoPoint(41.161758, -8.583933), Metre(117)),
            LookupResult(GeoPoint(85.0, 10.1), Metre(0), "Missing elevation data for (85.0,10.1)"),
        )
    }

    override val controller: RaylevationController = RaylevationController(LocationsParserImpl(), service)

    override val additionalControllerAdvices: Array<Any> = arrayOf(RaylevationControllerAdvisor())
}
