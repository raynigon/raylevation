package com.raynigon.raylevation.base.service

import static com.raynigon.unit.api.core.units.si.SISystemUnitsConstants.Metre

import com.raynigon.ecs.logging.async.service.AsyncService
import com.raynigon.ecs.logging.async.service.NoOpAsyncService
import com.raynigon.raylevation.db.IRaylevationDB
import com.raynigon.raylevation.infrastructure.model.GeoPoint
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.util.concurrent.CompletableFuture
import java.util.function.Supplier


class RaylevationServiceSpec extends Specification {

    IRaylevationDB database = Mock()

    RaylevationDBFactory factory = Mock()

    AsyncService asyncService = Mock()

    @Subject
    RaylevationService service

    def setup() {
        factory.create() >> database
        service = new RaylevationServiceImpl(factory, asyncService)
    }

    def "database gets loaded correctly"() {
        given:
        RaylevationDBFactory factory = Mock()

        when:
        new RaylevationServiceImpl(factory, new NoOpAsyncService())

        then:
        1 * factory.create() >> database
    }

    def "lookup gets executed on database"() {
        given:
        def query = [new GeoPoint(1.0, 2.0)]

        when:
        service.lookup(query)

        then:
        1 * database.lookupElevation(new GeoPoint(1.0, 2.0)) >> Metre(1)
    }

    @Unroll
    def "lookup gets chunked - #items with #asyncCalls async calls"() {
        given:
        GeoPoint[] query = new GeoPoint[items]
        Arrays.fill(query, new GeoPoint(1.0, 2.0));

        when:
        service.lookup(query.toList())

        then:
        items * database.lookupElevation(_) >> Metre(1)
        asyncCalls * asyncService.supplyAsync(_) >> { Supplier supplier -> CompletableFuture.supplyAsync(supplier) }

        where:
        items | asyncCalls
        1     | 0
        1000  | 0
        10000 | 0
        10001 | 2
        20000 | 2
        20001 | 3
    }

    def "database cache sync gets executed"() {
        when:
        service.syncCache()

        then:
        1 * database.syncCache()
    }
}
