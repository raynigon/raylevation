package com.raynigon.raylevation.db.lock

import java.util.UUID

/**
 * If an error occurred during database locking, this exception is thrown
 */
sealed class LockException(val lockId: UUID, message: String? = null) :
    RuntimeException(message)

/**
 * If the given lock could not be acquired in the given timeframe,
 * this exception is thrown
 */
class LockTimeoutException(lockId: UUID, message: String? = null) :
    LockException(lockId, message)

/**
 * If the given lock was locked but was then stolen by another process,
 * this exception is thrown
 */
class StolenLockException(lockId: UUID, val otherLockId: UUID? = null) :
    LockException(lockId, "Lock was stolen by $otherLockId")

/**
 * If the given lock should be locked but is not,
 * this exception is thrown
 */
class UnlockedException(lockId: UUID) :
    LockException(lockId, "Lock $lockId is not locked. Unable to execute operation")
