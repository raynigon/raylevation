package com.raynigon.raylevation.db.lock

import java.time.OffsetDateTime
import java.util.UUID

/**
 * Data transfer object which is serialized to json
 */
data class LockContent(
    val lockId: UUID,
    val updated: OffsetDateTime
)
