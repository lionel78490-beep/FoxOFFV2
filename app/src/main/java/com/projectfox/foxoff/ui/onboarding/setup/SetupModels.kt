package com.projectfox.foxoff.ui.onboarding.setup

enum class SetupStepStatus {
    IDLE,
    IN_PROGRESS,
    COMPLETED,
    ERROR
}

data class SetupStepInfo(
    val id: String,
    val icon: String,
    val title: String,
    val currentMessage: String,
    val status: SetupStepStatus,
    val progress: Float = 0f
)

data class EnvironmentSetupState(
    val watchStep: SetupStepInfo,
    val tvStep: SetupStepInfo,
    val sensorsStep: SetupStepInfo,
    val brainStep: SetupStepInfo,
    val overallProgress: Float = 0f,
    val isAllCompleted: Boolean = false
)
