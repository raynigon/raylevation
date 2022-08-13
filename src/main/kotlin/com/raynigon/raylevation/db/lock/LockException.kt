package com.raynigon.raylevation.db.lock

import java.util.UUID

sealed class LockException(val lockId: UUID, message: String? = null) :
    RuntimeException(message)

class LockTimeoutException(lockId: UUID, message: String? = null) :
    LockException(lockId, message)

class StolenLockException(lockId: UUID, val otherLockId: UUID? = null) :
    LockException(lockId, "Lock was stolen by $otherLockId")

class UnlockedException(lockId: UUID) :
    LockException(lockId, "Lock $lockId is not locked. Unable to execute operation")
