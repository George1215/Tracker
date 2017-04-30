package benson.tracker.domain.model

data class TrackedUser(val username: String)

data class GPSUser(
        val username: String?,
        val date: String?,
        val distanceWalked: Double?,
        val idleTime: Long?,
        val movinTime: Long?)