package com.raynigon.raylevation.db.lock

import java.time.OffsetDateTime
import java.util.UUID

data class LockContent(
    val lockId: UUID,
    val updated: OffsetDateTime
)
