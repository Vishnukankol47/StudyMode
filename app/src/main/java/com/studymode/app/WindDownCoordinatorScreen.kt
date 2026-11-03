package com.studymode.app

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

enum class WindDownDestination {
    MAIN,
    LIBRARY,
    ADD_AUDIO,
    PLAYER,
    ACTIVE_SESSION,
    SETTINGS,
    RELAXING_SOUNDS,
}

@Composable
fun WindDownCoordinatorScreen(
    store: LocalStudyStore,
    initialDestination: WindDownDestination = WindDownDestination.MAIN,
    onBackToSleep: () -> Unit,
    onNavigateToBlocks: () -> Unit = {},
) {
    val context = LocalContext.current

    // Initialize playback manager once
    LaunchedEffect(Unit) {
        WindDownAudioPlaybackManager.initialize(context)
    }

    var destinationStack by remember { mutableStateOf(listOf(initialDestination)) }
    val currentDestination = destinationStack.lastOrNull() ?: WindDownDestination.MAIN

    var activePlayerTrack by remember {
        mutableStateOf(
            WindDownAudioPlaybackManager.currentTrack.value ?: store.windDownTracks().firstOrNull() ?: WindDownAudioTrack(title = "Chill Study Mix", uri = "")
        )
    }

    fun navigateTo(dest: WindDownDestination) {
        destinationStack = destinationStack + dest
    }

    fun popBack() {
        if (destinationStack.size > 1) {
            destinationStack = destinationStack.dropLast(1)
        } else {
            onBackToSleep()
        }
    }

    BackHandler(enabled = true) {
        popBack()
    }

    when (currentDestination) {
        WindDownDestination.MAIN -> {
            WindDownMainScreen(
                store = store,
                onBack = { onBackToSleep() },
                onNavigateToLibrary = { navigateTo(WindDownDestination.LIBRARY) },
                onNavigateToAddAudio = { navigateTo(WindDownDestination.ADD_AUDIO) },
                onNavigateToPlayer = { track ->
                    activePlayerTrack = track
                    navigateTo(WindDownDestination.PLAYER)
                },
                onNavigateToActiveSession = { navigateTo(WindDownDestination.ACTIVE_SESSION) },
                onNavigateToSettings = { navigateTo(WindDownDestination.SETTINGS) },
                onNavigateToRelaxingSounds = { navigateTo(WindDownDestination.RELAXING_SOUNDS) },
                onNavigateToBlocks = onNavigateToBlocks,
            )
        }
        WindDownDestination.LIBRARY -> {
            MyAudioLibraryScreen(
                store = store,
                onBack = { popBack() },
                onNavigateToAddAudio = { navigateTo(WindDownDestination.ADD_AUDIO) },
                onNavigateToPlayer = { track ->
                    activePlayerTrack = track
                    navigateTo(WindDownDestination.PLAYER)
                },
            )
        }
        WindDownDestination.ADD_AUDIO -> {
            AddAudioScreen(
                store = store,
                onBack = { popBack() },
                onNavigateToLibrary = {
                    // Pop ADD_AUDIO and navigate to LIBRARY
                    destinationStack = listOf(WindDownDestination.MAIN, WindDownDestination.LIBRARY)
                },
            )
        }
        WindDownDestination.PLAYER -> {
            AudioPlayerScreen(
                store = store,
                track = activePlayerTrack,
                onBack = { popBack() },
            )
        }
        WindDownDestination.ACTIVE_SESSION -> {
            WindDownActiveSessionScreen(
                store = store,
                onBack = { popBack() },
                onNavigateToPlayer = { track ->
                    activePlayerTrack = track
                    navigateTo(WindDownDestination.PLAYER)
                },
            )
        }
        WindDownDestination.SETTINGS -> {
            AudioSettingsScreen(
                store = store,
                onBack = { popBack() },
            )
        }
        WindDownDestination.RELAXING_SOUNDS -> {
            RelaxingSoundsScreen(
                store = store,
                onBack = { popBack() },
            )
        }
    }
}
