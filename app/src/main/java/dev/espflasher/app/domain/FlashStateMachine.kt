package dev.espflasher.app.domain

object FlashStateMachine {
    private val transitions: Map<FlashPhase, Set<FlashPhase>> = mapOf(
        FlashPhase.Disconnected to setOf(FlashPhase.Connecting, FlashPhase.Error),
        FlashPhase.Connecting to setOf(FlashPhase.Detecting, FlashPhase.Disconnected, FlashPhase.Error),
        FlashPhase.Detecting to setOf(FlashPhase.Ready, FlashPhase.Disconnected, FlashPhase.Error),
        FlashPhase.Ready to setOf(FlashPhase.EnteringBootloader, FlashPhase.Erasing, FlashPhase.Writing, FlashPhase.Disconnected, FlashPhase.Error),
        FlashPhase.EnteringBootloader to setOf(FlashPhase.Erasing, FlashPhase.Writing, FlashPhase.Ready, FlashPhase.Disconnected, FlashPhase.Error),
        FlashPhase.Erasing to setOf(FlashPhase.Writing, FlashPhase.Disconnected, FlashPhase.Error),
        FlashPhase.Writing to setOf(FlashPhase.Verifying, FlashPhase.Resetting, FlashPhase.Success, FlashPhase.Disconnected, FlashPhase.Error),
        FlashPhase.Verifying to setOf(FlashPhase.Resetting, FlashPhase.Success, FlashPhase.Disconnected, FlashPhase.Error),
        FlashPhase.Resetting to setOf(FlashPhase.Success, FlashPhase.Disconnected, FlashPhase.Error),
        FlashPhase.Success to setOf(FlashPhase.Ready, FlashPhase.Disconnected),
        FlashPhase.Error to setOf(FlashPhase.Disconnected, FlashPhase.Connecting, FlashPhase.Ready),
    )

    fun canTransition(from: FlashPhase, to: FlashPhase): Boolean =
        from == to || transitions[from]?.contains(to) == true

    fun apply(from: FlashPhase, to: FlashPhase): FlashPhase {
        if (canTransition(from, to) || to == FlashPhase.Disconnected || to == FlashPhase.Error) return to
        error("Illegal flash state transition: $from → $to")
    }

    fun isFlashing(phase: FlashPhase) = phase in setOf(
        FlashPhase.EnteringBootloader, FlashPhase.Erasing, FlashPhase.Writing,
        FlashPhase.Verifying, FlashPhase.Resetting,
    )

    fun label(phase: FlashPhase) = when (phase) {
        FlashPhase.Disconnected -> "Waiting for device"
        FlashPhase.Connecting -> "Connecting"
        FlashPhase.Detecting -> "Detecting chip"
        FlashPhase.Ready -> "Ready to flash"
        FlashPhase.EnteringBootloader -> "Entering bootloader"
        FlashPhase.Erasing -> "Erasing"
        FlashPhase.Writing -> "Writing firmware"
        FlashPhase.Verifying -> "Verifying"
        FlashPhase.Resetting -> "Resetting"
        FlashPhase.Success -> "Complete"
        FlashPhase.Error -> "Error"
    }
}
