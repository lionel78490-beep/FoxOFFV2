package com.projectfox.foxoff.core.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceReconciliationDecisionTest {

    @Test
    fun `1 - enabled intent with STOPPED status and valid prerequisites starts`() {
        val action = ServiceReconciliationDecision.decide(
            intentEnabled = true,
            realStatus = FoxForegroundServiceStatus.STOPPED,
            bluetoothGranted = true,
            notificationsVisible = true
        )

        assertEquals(ServiceReconciliationAction.START, action)
    }

    @Test
    fun `1b - ERROR is a recoverable status, also starts when prerequisites are valid`() {
        val action = ServiceReconciliationDecision.decide(
            intentEnabled = true,
            realStatus = FoxForegroundServiceStatus.ERROR,
            bluetoothGranted = true,
            notificationsVisible = true
        )

        assertEquals(ServiceReconciliationAction.START, action)
    }

    @Test
    fun `2 - RUNNING or STARTING never triggers a restart`() {
        assertEquals(
            ServiceReconciliationAction.NO_OP,
            ServiceReconciliationDecision.decide(
                intentEnabled = true,
                realStatus = FoxForegroundServiceStatus.RUNNING,
                bluetoothGranted = true,
                notificationsVisible = true
            )
        )
        assertEquals(
            ServiceReconciliationAction.NO_OP,
            ServiceReconciliationDecision.decide(
                intentEnabled = true,
                realStatus = FoxForegroundServiceStatus.STARTING,
                bluetoothGranted = true,
                notificationsVisible = true
            )
        )
    }

    @Test
    fun `3 - disabled intent with STOPPED status is a no-op (already matches intent)`() {
        val action = ServiceReconciliationDecision.decide(
            intentEnabled = false,
            realStatus = FoxForegroundServiceStatus.STOPPED,
            bluetoothGranted = true,
            notificationsVisible = true
        )

        assertEquals(ServiceReconciliationAction.NO_OP, action)
    }

    @Test
    fun `3b - disabled intent with a still-RUNNING service stops it`() {
        val action = ServiceReconciliationDecision.decide(
            intentEnabled = false,
            realStatus = FoxForegroundServiceStatus.RUNNING,
            bluetoothGranted = true,
            notificationsVisible = true
        )

        assertEquals(ServiceReconciliationAction.STOP, action)
    }

    @Test
    fun `4 - notifications unavailable disables and stops`() {
        val action = ServiceReconciliationDecision.decide(
            intentEnabled = true,
            realStatus = FoxForegroundServiceStatus.STOPPED,
            bluetoothGranted = true,
            notificationsVisible = false
        )

        assertEquals(ServiceReconciliationAction.DISABLE_AND_STOP, action)
    }

    @Test
    fun `5 - bluetooth not granted disables and stops`() {
        val action = ServiceReconciliationDecision.decide(
            intentEnabled = true,
            realStatus = FoxForegroundServiceStatus.STOPPED,
            bluetoothGranted = false,
            notificationsVisible = true
        )

        assertEquals(ServiceReconciliationAction.DISABLE_AND_STOP, action)
    }
}
