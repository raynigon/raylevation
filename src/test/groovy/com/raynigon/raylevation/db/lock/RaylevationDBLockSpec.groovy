package com.raynigon.raylevation.db.lock

import com.raynigon.raylevation.infrastructure.configuration.JacksonConfiguration
import com.fasterxml.jackson.databind.ObjectMapper
import com.raynigon.raylevation.infrastructure.configuration.JacksonConfiguration
import nl.altindag.log.LogCaptor
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

class RaylevationDBLockSpec extends Specification {

    @Shared
    ObjectMapper objectMapper = new JacksonConfiguration().objectMapper()

    LogCaptor logCaptor = LogCaptor.forClass(RaylevationDBLock.class)

    def setup() {
        logCaptor.clearLogs()
    }

    def "create lock"() {
        given:
        File targetFile = File.createTempDir()
        targetFile.deleteOnExit()
        Path target = targetFile.toPath()
        Path lockFile = target.resolve("lock.json")

        and:
        RaylevationDBLock lock = new RaylevationDBLock(lockFile, objectMapper)

        when:
        lock.lock(Duration.ofSeconds(1))

        then:
        Files.exists(lockFile)
    }

    def "replace outdated lock"() {
        given:
        File targetFile = File.createTempDir()
        targetFile.deleteOnExit()
        Path target = targetFile.toPath()
        Path lockFile = target.resolve("lock.json")

        and:
        UUID otherLockId = UUID.randomUUID()
        objectMapper.writeValue(lockFile.toFile(), new LockContent(otherLockId, OffsetDateTime.now().minusDays(1)))

        and:
        RaylevationDBLock lock = new RaylevationDBLock(lockFile, objectMapper)

        when:
        lock.lock(Duration.ofSeconds(1))

        then:
        Files.exists(lockFile)
        objectMapper.readValue(lockFile.toFile(), LockContent).lockId != otherLockId

        and:
        logCaptor.getWarnLogs().size() == 1
        logCaptor.getWarnLogs().get(0).contains(otherLockId.toString())
    }

    def "lock file gets overwritten"() {
        given:
        File targetFile = File.createTempDir()
        targetFile.deleteOnExit()
        Path target = targetFile.toPath()
        Path lockFile = target.resolve("lock.json")

        and:
        UUID otherLockId = UUID.randomUUID()

        and:
        RaylevationDBLock lock = new RaylevationDBLock(lockFile, objectMapper)

        when:
        def future = CompletableFuture.supplyAsync {
            lock.lock(Duration.ofSeconds(1))
        }

        and:
        while (!Files.exists(lockFile)) sleep(10)
        sleep(10)
        objectMapper.writeValue(lockFile.toFile(), new LockContent(otherLockId, OffsetDateTime.now()))
        sleep(10)
        future.get()

        then:
        ExecutionException exception = thrown(ExecutionException)
        exception.cause instanceof LockTimeoutException
        ((LockTimeoutException) exception.cause).lockId == lock.lockId

        and:
        Files.exists(lockFile)
        objectMapper.readValue(lockFile.toFile(), LockContent).lockId == otherLockId
        logCaptor.getWarnLogs().size() == 1
        logCaptor.getWarnLogs().get(0).contains("Lock was overwritten")
        logCaptor.getWarnLogs().get(0).contains(otherLockId.toString())
    }

    def "update valid lock"() {
        given:
        File targetFile = File.createTempDir()
        targetFile.deleteOnExit()
        Path target = targetFile.toPath()
        Path lockFile = target.resolve("lock.json")

        and:
        RaylevationDBLock lock = new RaylevationDBLock(lockFile, objectMapper)
        lock.lock(Duration.ofSeconds(1))
        objectMapper.writeValue(lockFile.toFile(), new LockContent(lock.lockId, OffsetDateTime.now().minusDays(1)))

        when:
        lock.update()

        and:
        def updated = objectMapper.readValue(lockFile.toFile(), LockContent).updated

        then:
        Files.exists(lockFile)
        Duration.between(updated, OffsetDateTime.now()) < Duration.ofSeconds(10)
    }

    def "update unlocked lock"() {
        given:
        File targetFile = File.createTempDir()
        targetFile.deleteOnExit()
        Path target = targetFile.toPath()
        Path lockFile = target.resolve("lock.json")

        and:
        RaylevationDBLock lock = new RaylevationDBLock(lockFile, objectMapper)

        when:
        lock.update()

        then:
        thrown(UnlockedException)
    }

    def "update invalid lock"() {
        given:
        File targetFile = File.createTempDir()
        targetFile.deleteOnExit()
        Path target = targetFile.toPath()
        Path lockFile = target.resolve("lock.json")

        and:
        RaylevationDBLock lock = new RaylevationDBLock(lockFile, objectMapper)
        lock.lock(Duration.ofSeconds(1))

        and:
        UUID otherLockId = UUID.randomUUID()
        objectMapper.writeValue(lockFile.toFile(), new LockContent(otherLockId, OffsetDateTime.now()))

        when:
        lock.update()

        then:
        StolenLockException exception = thrown(StolenLockException)
        exception.otherLockId == otherLockId
    }

    def "unlock valid lock"() {
        given:
        File targetFile = File.createTempDir()
        targetFile.deleteOnExit()
        Path target = targetFile.toPath()
        Path lockFile = target.resolve("lock.json")

        and:
        RaylevationDBLock lock = new RaylevationDBLock(lockFile, objectMapper)
        lock.lock(Duration.ofSeconds(1))

        expect:
        Files.exists(lockFile)

        when:
        lock.unlock()

        then:
        !Files.exists(lockFile)
    }

    def "unlock unlocked lock"() {
        given:
        File targetFile = File.createTempDir()
        targetFile.deleteOnExit()
        Path target = targetFile.toPath()
        Path lockFile = target.resolve("lock.json")

        and:
        RaylevationDBLock lock = new RaylevationDBLock(lockFile, objectMapper)

        when:
        lock.unlock()

        then:
        thrown(UnlockedException)
    }

    def "unlock invalid lock"() {
        given:
        File targetFile = File.createTempDir()
        targetFile.deleteOnExit()
        Path target = targetFile.toPath()
        Path lockFile = target.resolve("lock.json")

        and:
        RaylevationDBLock lock = new RaylevationDBLock(lockFile, objectMapper)
        lock.lock(Duration.ofSeconds(1))

        and:
        UUID otherLockId = UUID.randomUUID()
        objectMapper.writeValue(lockFile.toFile(), new LockContent(otherLockId, OffsetDateTime.now()))

        when:
        lock.unlock()

        then:
        StolenLockException exception = thrown(StolenLockException)
        exception.otherLockId == otherLockId
    }
}
