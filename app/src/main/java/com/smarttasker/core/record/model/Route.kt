package com.smarttasker.core.record.model

data class RouteDraft(
    val routeId: String = java.util.UUID.randomUUID().toString().take(8),
    val name: String = "",
    val source: RouteSource = RouteSource.MANUAL_RECORDING,
    val status: RouteStatus = RouteStatus.DRAFT,
    val deviceProfile: DeviceProfile? = null,
    val appScope: AppContextSnapshot? = null,
    val steps: List<RecordedStep> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class RouteSource { MANUAL_RECORDING, AI_LEARNED, USER_EDIT, TEMPLATE }
enum class RouteStatus { DRAFT, PUBLISHED, ARCHIVED }
