package com.moa.service.dto

import java.time.Duration
import java.time.Instant

data class DeployInfoResponse(
    val version: String,
    val color: String,
    val startedAt: String,
    val uptime: String,
    val uptimeSeconds: Long,
) {
    companion object {
        fun of(version: String, color: String, startedAt: Instant): DeployInfoResponse {
            val duration = Duration.between(startedAt, Instant.now())
            return DeployInfoResponse(
                version = version,
                color = color,
                startedAt = startedAt.toString(),
                uptime = formatDuration(duration),
                uptimeSeconds = duration.toSeconds(),
            )
        }

        private fun formatDuration(duration: Duration): String =
            String.format(
                "%dh %dm %ds",
                duration.toHours(),
                duration.toMinutesPart(),
                duration.toSecondsPart(),
            )
    }
}
