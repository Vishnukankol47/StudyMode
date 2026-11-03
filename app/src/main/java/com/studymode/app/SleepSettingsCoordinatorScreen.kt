package com.studymode.app

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*

@Composable
fun SleepSettingsCoordinatorScreen(
    store: LocalStudyStore,
    initialPanel: SleepSettingsPanel = SleepSettingsPanel.HUB,
    onBack: () -> Unit,
    onNavigateToHome: () -> Unit = {},
    onNavigateToPlan: () -> Unit = {},
    onNavigateToBlocks: () -> Unit = {},
) {
    var currentPanel by remember { mutableStateOf(initialPanel) }

    BackHandler(enabled = true) {
        if (currentPanel == SleepSettingsPanel.HUB) {
            onBack()
        } else if (currentPanel == SleepSettingsPanel.CALMING_DISPLAY) {
            currentPanel = SleepSettingsPanel.DISPLAY
        } else {
            currentPanel = SleepSettingsPanel.HUB
        }
    }

    when (currentPanel) {
        SleepSettingsPanel.HUB -> {
            SleepSettingsHubScreen(
                store = store,
                onNavigate = { currentPanel = it },
                onBack = onBack,
                onNavigateToHome = onNavigateToHome,
                onNavigateToPlan = onNavigateToPlan,
                onNavigateToBlocks = onNavigateToBlocks,
            )
        }

        SleepSettingsPanel.SCHEDULE -> {
            SleepScheduleSettingsScreen(
                store = store,
                onBack = { currentPanel = SleepSettingsPanel.HUB },
            )
        }

        SleepSettingsPanel.WIND_DOWN -> {
            WindDownSettingsScreen(
                store = store,
                onBack = { currentPanel = SleepSettingsPanel.HUB },
            )
        }

        SleepSettingsPanel.SOUNDS -> {
            SleepSoundsSettingsScreen(
                store = store,
                onBack = { currentPanel = SleepSettingsPanel.HUB },
            )
        }

        SleepSettingsPanel.TRACKING -> {
            TrackingRemindersSettingsScreen(
                store = store,
                onBack = { currentPanel = SleepSettingsPanel.HUB },
            )
        }

        SleepSettingsPanel.DISPLAY -> {
            DisplayDeviceSettingsScreen(
                store = store,
                onNavigateToCalmingDisplay = { currentPanel = SleepSettingsPanel.CALMING_DISPLAY },
                onBack = { currentPanel = SleepSettingsPanel.HUB },
            )
        }

        SleepSettingsPanel.NOTIFICATIONS -> {
            SleepNotificationsSettingsScreen(
                store = store,
                onBack = { currentPanel = SleepSettingsPanel.HUB },
            )
        }

        SleepSettingsPanel.DATA_PRIVACY -> {
            DataPrivacySettingsScreen(
                store = store,
                onBack = { currentPanel = SleepSettingsPanel.HUB },
            )
        }

        SleepSettingsPanel.ADVANCED -> {
            AdvancedSettingsScreen(
                store = store,
                onBack = { currentPanel = SleepSettingsPanel.HUB },
            )
        }

        SleepSettingsPanel.CALMING_DISPLAY -> {
            CalmingSleepDisplayScreen(
                onDismiss = { currentPanel = SleepSettingsPanel.DISPLAY },
            )
        }
    }
}
