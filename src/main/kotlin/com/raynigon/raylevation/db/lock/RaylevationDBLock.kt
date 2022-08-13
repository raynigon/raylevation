package com.raynigon.raylevation.db.lock

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.lang.Thread.sleep
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Duration.between
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.TimeoutException

interface IRaylevationDBLock {

    val lockId: UUID

    val locked: Boolean

    /**
     * Lock the Database. A lock file will be created if no other lock exists.
     * If another lock already exists, it will be waited until the lock gets released,
     * the lock is no longer updated or [timeout] is reached.
     *
     * @param timeout             the maximum waiting duration for the lock to be acquired
     * @throws TimeoutException    thrown if the lock can not be acquired in [timeout]
     */
    fun lock(timeout: Duration)

    fun update()

    fun unlock()
}

fun IRaylevationDBLock?.isLocked(): Boolean {
    return this != null && this.locked
}

class RaylevationDBLock(
    private val lockFile: Path,
    private val objectMapper: ObjectMapper
) : IRaylevationDBLock {

    companion object {
        val LOCK_TIMEOUT: Duration = Duration.ofMinutes(15)
        val LOCKED_WAITING_PERIOD: Duration = Duration.ofMillis(100)
        val LOCK_CREATED_WAITING_PERIOD: Duration = Duration.ofSeconds(2).plusMillis(500)
    }

    private val logger = LoggerFactory.getLogger(this::class.java)
    private var _locked = false

    override val lockId = UUID.randomUUID()!!
    override val locked: Boolean get() = _locked

    @Synchronized
    override fun lock(timeout: Duration) {
        val start = Instant.now()
        while (true) {
            while (Files.exists(lockFile) && !checkLock()) {
                if (between(start, Instant.now()) > timeout) {
                    throw LockTimeoutException(lockId, "Unable to acquire lock in $timeout")
                }
                val content = readLock()
                if (content != null && between(content.updated, OffsetDateTime.now()) > LOCK_TIMEOUT) {
                    Files.delete(lockFile)
                    logger.warn("Deleted lock due to lock timeout. Current LockId: ${content.lockId} was updated at ${content.updated}")
                    continue
                }
                logger.debug("Unable to acquire lock. Current LockId: ${content?.lockId} was updated at ${content?.updated}")
                sleep(LOCKED_WAITING_PERIOD.toMillis())
            }
            writeLock()
            sleep(LOCK_CREATED_WAITING_PERIOD.toMillis())
            // Exit loop if lock was created
            if (checkLock()) break
            val content = readLock()
            logger.warn("Lock was overwritten. Current LockId: ${content?.lockId} was updated at ${content?.updated}")
        }
        _locked = true
    }

    @Synchronized
    override fun update() {
        if (!locked) throw UnlockedException(lockId)
        if (!checkLock()) {
            throw StolenLockException(lockId, readLock()?.lockId)
        }
        writeLock()
    }

    @Synchronized
    override fun unlock() {
        if (!locked) throw UnlockedException(lockId)
        if (!checkLock()) {
            throw StolenLockException(lockId, readLock()?.lockId)
        }
        _locked = false
        Files.delete(lockFile)
    }

    private fun writeLock() {
        objectMapper.writeValue(lockFile.toFile(), LockContent(lockId, OffsetDateTime.now()))
    }

    private fun checkLock(): Boolean {
        val content = readLock() ?: return false
        return content.lockId == lockId
    }

    private fun readLock(): LockContent? {
        if (!Files.exists(lockFile)) return null
        return try {
            objectMapper.readValue(lockFile.toFile(), LockContent::class.java)
        } catch (e: Exception) {
            logger.debug("Unable to check lock", e)
            null
        }
    }
}
