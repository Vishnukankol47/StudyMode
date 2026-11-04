package com.studymode.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * High-performance Shorts & Reels Micro-Blocker & Scroll Detector.
 *
 * Updated for modern 2024/2025/2026 YouTube Shorts & Instagram Reels UI.
 *
 * Detection strategy (layered, any match = Shorts active):
 *   1. Bottom nav Shorts/Reels TAB is selected (most reliable, works 80% of time)
 *   2. Modern resource IDs for Shorts/Reels containers (shorts_container, reel_player, reel_recycler)
 *   3. Creator handle starting with '@' + inline Subscribe button (definitive Shorts player layout)
 *   4. Engagement button signatures: Inline Subscribe, Remix/Create/Sample, reel share, audio track
 *   5. Absence of long-form SeekBar (timestamp scrubber) = NOT a regular video player
 *   6. Text/description markers: "Shorts", "Reels", "Spotlight"
 *
 * Session Latch & Grace Period:
 *   - Once Shorts/Reels mode is confirmed, a latch keeps the session alive even if YouTube dims
 *     or fades out control nodes after a few seconds of watching without touching the screen.
 *   - Transient System UI events (volume adjustment, notification banners, IME) are isolated so
 *     they never disrupt or dismiss the floating HUD.
 *   - A 3.0s grace period prevents overlay flickering during swipes and video transitions.
 *
 * Platforms: YouTube (standard, ReVanced, RVX), Instagram, Snapchat, Facebook
 */
object ShortsReelsDetector {

    private const val THROTTLE_INTERVAL_MILLIS = 350L
    private var lastInterceptMillis = 0L

    private var lastShortDigest: String = ""
    private var lastScrollEventMillis = 0L

    // Session Latch State (persists HUD during playback even when controls fade)
    @Volatile
    private var isShortsSessionActive = false
    @Volatile
    private var activePlatform = ""
    private var lastShortsConfirmedMillis = 0L
    private const val SESSION_GRACE_PERIOD_MILLIS = 3000L // 3.0s grace period

    fun checkAndIntercept(
        service: AccessibilityService,
        event: AccessibilityEvent?,
        store: LocalStudyStore,
    ): Boolean {
        val microBlockMaster = store.isBlockShortsReelsEnabled()
        val scrollCounterEnabled = store.isFloatingScrollCounterEnabled()

        if (!microBlockMaster && !scrollCounterEnabled) {
            isShortsSessionActive = false
            activePlatform = ""
            lastShortDigest = ""
            FloatingScrollCounterOverlayManager.hide(service)
            return false
        }

        val eventPkg = event?.packageName?.toString()
        val rootPkg = try { service.rootInActiveWindow?.packageName?.toString() } catch (_: Exception) { null }
        val packageName = eventPkg ?: rootPkg ?: return false

        // 1. Transient system UI (volume change, heads-up notifications, IME)
        // should never disrupt an active Shorts/Reels session.
        if (isSystemOrTransientPackage(packageName)) {
            return isShortsSessionActive
        }

        val isYouTube = packageName == "com.google.android.youtube" ||
                packageName == "app.revanced.android.youtube" ||
                packageName == "app.rvx.android.youtube"

        val isInstagram = packageName == "com.instagram.android" ||
                packageName == "com.instagram.lite"

        val isSnapchat = packageName == "com.snapchat.android" ||
                packageName == "com.snap.android"

        val isFacebook = packageName == "com.facebook.katana" ||
                packageName == "com.facebook.lite"

        // 2. User left all short-video host applications (switched to home launcher or another app)
        if (!isYouTube && !isInstagram && !isSnapchat && !isFacebook) {
            isShortsSessionActive = false
            activePlatform = ""
            lastShortDigest = ""
            FloatingScrollCounterOverlayManager.hide(service)
            return false
        }

        val rootNode = service.rootInActiveWindow ?: event?.source
        val now = System.currentTimeMillis()

        if (rootNode == null) {
            // Root window momentarily null during animated transition
            if (isShortsSessionActive && (now - lastShortsConfirmedMillis < SESSION_GRACE_PERIOD_MILLIS)) {
                return true
            }
            isShortsSessionActive = false
            activePlatform = ""
            lastShortDigest = ""
            FloatingScrollCounterOverlayManager.hide(service)
            return false
        }

        val detectionResult = when {
            isYouTube -> detectYouTubeShorts(rootNode)
            isInstagram -> detectInstagramReels(rootNode)
            isSnapchat -> detectSnapchatSpotlight(rootNode)
            isFacebook -> detectFacebookReels(rootNode)
            else -> null
        }

        if (isYouTube || isInstagram) {
            android.util.Log.d("StudyMode", "ShortsReelsDetector: pkg=$packageName, event=${event?.eventType}, match=${detectionResult?.platformName}, isSession=$isShortsSessionActive")
        }

        // 3. Handle detection results with Session Latch & Grace Period
        if (detectionResult == null) {
            if (isShortsSessionActive) {
                // Check if user has explicitly exited to a long-form video or home feed
                val hasExplicitExit = when {
                    isYouTube -> isExplicitYouTubeExit(rootNode)
                    isInstagram -> isExplicitInstagramExit(rootNode)
                    else -> false
                }

                if (!hasExplicitExit) {
                    // Controls simply faded out during short video playback!
                    // Maintain active session and keep HUD visible & ticking
                    if (scrollCounterEnabled) {
                        FloatingScrollCounterOverlayManager.updateCounter(
                            context = service,
                            platformName = activePlatform.ifEmpty { if (isYouTube) "YouTube Shorts" else "Instagram Reels" },
                            isScrollEvent = false,
                            store = store,
                            onLimitReached = {
                                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                                ShortsBlockOverlayManager.show(service, "$activePlatform (Limit Reached)")
                            }
                        )
                    }
                    return true
                }
            }

            // Explicit exit: user navigated back to home feed or regular video
            isShortsSessionActive = false
            activePlatform = ""
            lastShortDigest = ""
            FloatingScrollCounterOverlayManager.hide(service)
            return false
        }

        // Confirmed Shorts/Reels active!
        isShortsSessionActive = true
        activePlatform = detectionResult.platformName
        lastShortsConfirmedMillis = now

        val platformName = detectionResult.platformName
        val homeTabToClick = detectionResult.homeTabNode

        val isMicroBlockActive = microBlockMaster && when {
            isYouTube -> store.isYouTubeShortsBlocked()
            isInstagram -> store.isInstagramReelsBlocked()
            isSnapchat -> store.isSnapchatSpotlightBlocked()
            isFacebook -> store.isFacebookReelsBlocked()
            else -> false
        }

        // Priority 1: Mindful Floating Scroll Counter HUD (Option B)
        // If user enabled the Live Floating Scroll Counter, display HUD over Shorts/Reels
        // and enforce the session limit (if limit > 0)
        if (scrollCounterEnabled) {
            val isScrollEvent = event?.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
            val digest = detectionResult.digest

            var isNewReel = false

            // Digest change indicates user swiped to a different short video
            if (digest.isNotEmpty() && lastShortDigest.isNotEmpty() && digest != lastShortDigest) {
                if (now - lastScrollEventMillis > 400L) {
                    lastScrollEventMillis = now
                    isNewReel = true
                }
                lastShortDigest = digest
            } else if (lastShortDigest.isEmpty() && digest.isNotEmpty()) {
                lastShortDigest = digest
            }

            // Scroll event fired
            if (isScrollEvent && (now - lastScrollEventMillis > 400L)) {
                lastScrollEventMillis = now
                isNewReel = true
            }

            FloatingScrollCounterOverlayManager.updateCounter(
                context = service,
                platformName = platformName,
                isScrollEvent = isNewReel,
                store = store,
                onLimitReached = {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    homeTabToClick?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    ShortsBlockOverlayManager.show(service, "$platformName (Limit Reached)")
                }
            )
            return true
        }

        // Priority 2: Zero-Tolerance Micro-Blocker Enforcement (Option A)
        // If floating counter is disabled, but micro-blocker is active, block immediately
        if (isMicroBlockActive) {
            isShortsSessionActive = false
            if (now - lastInterceptMillis >= THROTTLE_INTERVAL_MILLIS) {
                lastInterceptMillis = now
                store.incrementShortsBlockedCount()

                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

                var homeClicked = false
                try {
                    homeClicked = homeTabToClick?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                } catch (_: Exception) {}

                if (!homeClicked && isYouTube) {
                    try {
                        val launchIntent = service.packageManager.getLaunchIntentForPackage(packageName)
                        launchIntent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        if (launchIntent != null) {
                            service.startActivity(launchIntent)
                        }
                    } catch (_: Exception) {}
                }

                ShortsBlockOverlayManager.show(service, platformName)
                FloatingScrollCounterOverlayManager.hide(service)
            }
            return true
        }

        return false
    }

    data class DetectionResult(
        val platformName: String,
        val digest: String = "",
        val homeTabNode: AccessibilityNodeInfo? = null,
    )

    // ─────────────────────────────────────────────────────────────
    // YouTube Shorts  (Modern Scrub-Tolerant & Hashing)
    // ─────────────────────────────────────────────────────────────
    private fun detectYouTubeShorts(root: AccessibilityNodeInfo): DetectionResult? {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        flattenNodes(root, nodes, maxNodes = 450)

        var isShortsTabSelected = false
        var homeTabNode: AccessibilityNodeInfo? = null

        var hasShortsByResourceId = false
        var hasInlineSubscribeButton = false
        var hasCreateOrSampleButton = false
        var hasReelShareOrSound = false
        var hasShortsTextOrDesc = false
        var hasYouTubeLogo = false
        var channelHandleText = ""

        val engagementNumbers = mutableListOf<String>()
        val contentTexts = mutableListOf<String>()

        // Number regex for like/comment/remix counts (e.g. 2.3K, 40, 1, 3.1K, 202)
        val engagementCountPattern = Regex("""^(\d+(\.\d+)?[KM]?)$""", RegexOption.IGNORE_CASE)

        for (node in nodes) {
            val desc = node.contentDescription?.toString() ?: ""
            val text = node.text?.toString() ?: ""
            val descLower = desc.lowercase()
            val textLower = text.lowercase()
            val resId = node.viewIdResourceName ?: ""
            val resIdLower = resId.lowercase()

            if (text.isNotBlank() && text.length in 2..120) contentTexts.add(text)

            // Extract numeric engagement counts
            val trimmedText = text.trim()
            val trimmedDesc = desc.trim()
            if (engagementCountPattern.matches(trimmedText)) {
                engagementNumbers.add(trimmedText)
            } else if (engagementCountPattern.matches(trimmedDesc)) {
                engagementNumbers.add(trimmedDesc)
            }

            // Creator handle starting with '@' (e.g. @flatlandstories, @SujithKottakkal)
            if (channelHandleText.isEmpty()) {
                if (text.startsWith("@") && text.length in 2..40) {
                    channelHandleText = text
                } else if (desc.startsWith("@") && desc.length in 2..40) {
                    channelHandleText = desc
                }
            }

            // Bottom nav tab detection (supports "Shorts", "Shorts, selected", "Selected, Shorts")
            if (descLower.contains("shorts") &&
                (node.isSelected || node.isFocused || descLower.contains("selected"))
            ) {
                isShortsTabSelected = true
            }
            if ((descLower.contains("home") || descLower.contains("explore")) && homeTabNode == null) {
                homeTabNode = node
            }

            // Resource ID signals
            if (resIdLower.contains("shorts") ||
                resIdLower.contains("reel") ||
                resIdLower.contains("reel_player") ||
                resIdLower.contains("shorts_container") ||
                resIdLower.contains("reel_watch") ||
                resIdLower.contains("reel_recycler") ||
                resIdLower.contains("shorts_shelf")
            ) {
                hasShortsByResourceId = true
            }

            // YouTube header logo on home/feed screens
            if (descLower == "youtube" || descLower.contains("youtube logo")) {
                hasYouTubeLogo = true
            }

            // Inline Subscribe button (floats on Shorts player)
            if ((descLower == "subscribe" || textLower == "subscribe" ||
                 descLower == "subscribed" || textLower == "subscribed") &&
                !resIdLower.contains("channel_item")
            ) {
                hasInlineSubscribeButton = true
            }

            // Remix / Create / Sample buttons
            if (descLower.contains("create with this") ||
                descLower.contains("sample this") ||
                descLower.contains("stitch") ||
                descLower.contains("remix") ||
                textLower.contains("remix") ||
                descLower == "create" ||
                descLower == "sample"
            ) {
                hasCreateOrSampleButton = true
            }

            // Audio / Share / Like / Comment specific to Shorts player action pillar
            if (descLower.contains("sound") ||
                descLower.contains("audio track") ||
                descLower.contains("original audio") ||
                descLower.contains("original sound") ||
                descLower == "share" ||
                textLower == "share" ||
                descLower.contains("dislike this video") ||
                descLower.contains("comments") ||
                descLower.contains("like this video") ||
                resIdLower.contains("reel_share") ||
                resIdLower.contains("shorts_share") ||
                resIdLower.contains("share_button") ||
                resIdLower.contains("like_button") ||
                resIdLower.contains("comment_button")
            ) {
                hasReelShareOrSound = true
            }

            // "Shorts" text visible on screen
            if (descLower.contains("shorts") || textLower.contains("shorts") ||
                resIdLower.contains("shorts")
            ) {
                hasShortsTextOrDesc = true
            }
        }

        // LikeCommentDigest combining channel handle, engagement numbers and title
        val numbersDigest = engagementNumbers.take(4).joinToString("#")
        val contentDigest = contentTexts.take(3).joinToString("|")
        val digest = when {
            channelHandleText.isNotBlank() && numbersDigest.isNotBlank() ->
                "$channelHandleText#$numbersDigest"
            channelHandleText.isNotBlank() ->
                "$channelHandleText|$contentDigest"
            numbersDigest.isNotBlank() ->
                "$numbersDigest|$contentDigest"
            else ->
                contentDigest
        }

        // If top YouTube header logo is present, we are on the Home feed or Search page, NOT in full Shorts
        if (hasYouTubeLogo && !isShortsTabSelected) {
            return null
        }

        // Case 1: Shorts bottom tab is explicitly selected — definitive
        if (isShortsTabSelected) {
            return DetectionResult("YouTube Shorts", digest, homeTabNode)
        }

        // Case 2: Channel handle starting with '@' + Subscribe button (Definitive Shorts layout)
        if (channelHandleText.isNotBlank() && hasInlineSubscribeButton) {
            return DetectionResult("YouTube Shorts", digest, homeTabNode)
        }

        // Case 3: Shorts container resource ID found
        if (hasShortsByResourceId) {
            return DetectionResult("YouTube Shorts", digest, homeTabNode)
        }

        // Case 4: "Remix" / "Create with this" button found
        if (hasCreateOrSampleButton) {
            return DetectionResult("YouTube Shorts", digest, homeTabNode)
        }

        // Case 5: Engagement buttons unique to Shorts player (at least 2 matching)
        val engagementScore = listOf(
            hasInlineSubscribeButton,
            hasCreateOrSampleButton,
            hasReelShareOrSound,
            hasShortsTextOrDesc,
            channelHandleText.isNotBlank(),
            engagementNumbers.size >= 2
        ).count { it }

        if (engagementScore >= 2) {
            return DetectionResult("YouTube Shorts", digest, homeTabNode)
        }

        // Case 6: "Shorts" text/badge visible with inline Subscribe
        if (hasShortsTextOrDesc && hasInlineSubscribeButton) {
            return DetectionResult("YouTube Shorts", digest, homeTabNode)
        }

        return null
    }

    private fun isExplicitYouTubeExit(root: AccessibilityNodeInfo): Boolean {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        flattenNodes(root, nodes, maxNodes = 120)

        // Regex for long-form video time indicator e.g. "0:15 / 10:24"
        val longFormDurationPattern = Regex("""\d+:\d+\s*/\s*\d+:\d+""")

        for (node in nodes) {
            val desc = node.contentDescription?.toString() ?: ""
            val descLower = desc.lowercase()
            val text = node.text?.toString() ?: ""
            val resId = node.viewIdResourceName?.lowercase() ?: ""

            // YouTube logo top header = back on Home/Explore feed
            if (descLower == "youtube" || descLower.contains("youtube logo")) return true

            // Search results feed or drawer
            if (resId.contains("results_recycler") || resId.contains("watch_panel")) return true

            // Home or Subscriptions bottom tab explicitly selected
            if ((descLower.contains("home") || descLower.contains("subscriptions")) &&
                (node.isSelected || descLower.contains("selected"))
            ) {
                return true
            }

            // Long-form video player time text like "1:23 / 15:45"
            if (longFormDurationPattern.containsMatchIn(text) || longFormDurationPattern.containsMatchIn(desc)) {
                return true
            }

            // Long-form specific video control buttons (Never present in Shorts)
            if (descLower.contains("rewind 10") || descLower.contains("fast forward 10") ||
                resId.contains("rewind") || resId.contains("fast_forward")
            ) {
                return true
            }
        }
        return false
    }

    // ─────────────────────────────────────────────────────────────
    // Instagram Reels  (Deep Scan - 2024/2025/2026 Litho/Compose)
    // ─────────────────────────────────────────────────────────────
    private fun detectInstagramReels(root: AccessibilityNodeInfo): DetectionResult? {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        flattenNodes(root, nodes, maxNodes = 450)

        var isReelsTabSelected = false
        var homeTabNode: AccessibilityNodeInfo? = null
        var hasReelsByResourceId = false
        var hasLikeButton = false
        var hasCommentButton = false
        var hasShareButton = false
        var hasAudioTrack = false
        var hasFollowButton = false
        var hasReelsHeader = false
        var creatorHandle = ""

        val engagementNumbers = mutableListOf<String>()
        val contentTexts = mutableListOf<String>()
        val engagementCountPattern = Regex("""^(\d+(\.\d+)?[KM]?)$""", RegexOption.IGNORE_CASE)

        for (node in nodes) {
            val desc = node.contentDescription?.toString() ?: ""
            val text = node.text?.toString() ?: ""
            val descLower = desc.lowercase()
            val textLower = text.lowercase()
            val resId = node.viewIdResourceName?.lowercase() ?: ""

            if (text.isNotBlank() && text.length in 2..80) contentTexts.add(text)

            // Numeric counts (like / comment / share numbers)
            val trimmedText = text.trim()
            val trimmedDesc = desc.trim()
            if (engagementCountPattern.matches(trimmedText)) {
                engagementNumbers.add(trimmedText)
            } else if (engagementCountPattern.matches(trimmedDesc)) {
                engagementNumbers.add(trimmedDesc)
            }

            // Creator handle starting with '@'
            if (creatorHandle.isEmpty()) {
                if (text.startsWith("@") && text.length in 2..40) {
                    creatorHandle = text
                } else if (desc.startsWith("@") && desc.length in 2..40) {
                    creatorHandle = desc
                }
            }

            // Bottom Navigation Tabs (supports "Reels", "Reels, selected", "Reels, tab")
            if (descLower.contains("reels") || descLower.contains("reel")) {
                if (node.isSelected || node.isFocused || descLower.contains("selected") || descLower.contains("tab")) {
                    isReelsTabSelected = true
                }
            }
            if ((descLower.contains("home") || descLower.contains("feed") || descLower.contains("main")) && homeTabNode == null) {
                homeTabNode = node
            }

            // Resource IDs
            if (resId.contains("reel") ||
                resId.contains("clips") ||
                resId.contains("unified_clips") ||
                resId.contains("clips_video_container") ||
                resId.contains("reel_viewer") ||
                resId.contains("clips_swipe_refresh_container") ||
                resId.contains("clips_action_button") ||
                resId.contains("clips_media_item") ||
                resId.contains("reel_tag")
            ) {
                hasReelsByResourceId = true
            }

            // Engagement / Action Buttons
            if (descLower == "like" || descLower.contains("like reel") ||
                descLower.contains("double tap to like") || descLower == "liked" ||
                resId.contains("clips_like") || resId.contains("like_button")
            ) {
                hasLikeButton = true
            }
            if (descLower == "comment" || descLower.contains("comments") ||
                descLower.contains("comment on reel") || resId.contains("comment")
            ) {
                hasCommentButton = true
            }
            if (descLower == "share" || descLower.contains("share reel") ||
                descLower.contains("send post") || descLower.contains("send reel") ||
                resId.contains("share") || resId.contains("clips_share")
            ) {
                hasShareButton = true
            }
            if (descLower.contains("audio") || descLower.contains("sound") ||
                descLower.contains("original audio") || resId.contains("audio")
            ) {
                hasAudioTrack = true
            }
            if (descLower == "follow" || textLower == "follow" ||
                descLower == "following" || textLower == "following"
            ) {
                hasFollowButton = true
            }

            // Header text "Reels" at top
            if (textLower == "reels" || descLower == "reels") {
                hasReelsHeader = true
            }
        }

        // Digest combining creator handle, counts and text for swipe detection
        val numbersDigest = engagementNumbers.take(4).joinToString("#")
        val contentDigest = contentTexts.take(3).joinToString("|")
        val digest = when {
            creatorHandle.isNotBlank() && numbersDigest.isNotBlank() -> "$creatorHandle#$numbersDigest"
            creatorHandle.isNotBlank() -> "$creatorHandle|$contentDigest"
            numbersDigest.isNotBlank() -> "$numbersDigest|$contentDigest"
            else -> contentDigest
        }

        // Case 1: Bottom Reels tab selected — definitive
        if (isReelsTabSelected) {
            return DetectionResult("Instagram Reels", digest, homeTabNode)
        }

        // Case 2: Clips/Reels container resource ID
        if (hasReelsByResourceId) {
            return DetectionResult("Instagram Reels", digest, homeTabNode)
        }

        // Case 3: Action pillar combinations characteristic of full-screen Reels viewer
        if ((hasLikeButton && hasCommentButton) || (hasLikeButton && hasAudioTrack)) {
            return DetectionResult("Instagram Reels", digest, homeTabNode)
        }

        // Case 4: Creator handle + Follow button + Share/Audio
        if (creatorHandle.isNotBlank() && (hasFollowButton || hasAudioTrack)) {
            return DetectionResult("Instagram Reels", digest, homeTabNode)
        }

        // Case 5: Reels header + at least 1 action button
        if (hasReelsHeader && (hasLikeButton || hasCommentButton || hasShareButton)) {
            return DetectionResult("Instagram Reels", digest, homeTabNode)
        }

        return null
    }

    private fun isExplicitInstagramExit(root: AccessibilityNodeInfo): Boolean {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        flattenNodes(root, nodes, maxNodes = 100)

        for (node in nodes) {
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val resId = node.viewIdResourceName?.lowercase() ?: ""
            val text = node.text?.toString()?.lowercase() ?: ""

            // Home / Feed tab selected
            if ((desc.contains("home") || desc.contains("feed")) && (node.isSelected || desc.contains("selected"))) return true
            // Messages / Direct
            if (resId.contains("feed_drawer") || desc.contains("messages") || desc.contains("direct") || text == "messages") return true
            // Profile tab selected
            if (desc.contains("profile") && (node.isSelected || desc.contains("selected"))) return true
            // Search / Explore tab selected
            if (desc.contains("search") && (node.isSelected || desc.contains("selected"))) return true
        }
        return false
    }

    // ─────────────────────────────────────────────────────────────
    // Snapchat Spotlight
    // ─────────────────────────────────────────────────────────────
    private fun detectSnapchatSpotlight(root: AccessibilityNodeInfo): DetectionResult? {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        flattenNodes(root, nodes, maxNodes = 150)

        val contentTexts = mutableListOf<String>()

        for (node in nodes) {
            val desc = node.contentDescription?.toString() ?: ""
            val text = node.text?.toString() ?: ""
            val descLower = desc.lowercase()
            val textLower = text.lowercase()
            val resId = node.viewIdResourceName?.lowercase() ?: ""

            if (text.isNotBlank()) contentTexts.add(text)

            val isSpotlight = (descLower.contains("spotlight") && (node.isSelected || node.isFocused)) ||
                resId.contains("spotlight") ||
                descLower == "spotlight" ||
                textLower == "spotlight"

            if (isSpotlight) {
                return DetectionResult("Snapchat Spotlight", contentTexts.take(3).joinToString("|"))
            }
        }
        return null
    }

    // ─────────────────────────────────────────────────────────────
    // Facebook Reels
    // ─────────────────────────────────────────────────────────────
    private fun detectFacebookReels(root: AccessibilityNodeInfo): DetectionResult? {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        flattenNodes(root, nodes, maxNodes = 150)

        val contentTexts = mutableListOf<String>()

        for (node in nodes) {
            val desc = node.contentDescription?.toString() ?: ""
            val text = node.text?.toString() ?: ""
            val descLower = desc.lowercase()
            val textLower = text.lowercase()
            val resId = node.viewIdResourceName?.lowercase() ?: ""

            if (text.isNotBlank()) contentTexts.add(text)

            val isFbReels = (descLower.contains("reels") && (node.isSelected || node.isFocused)) ||
                resId.contains("fb_reels") ||
                resId.contains("reels_viewer") ||
                descLower.contains("watch reels") ||
                (textLower == "reels" && node.isSelected)

            if (isFbReels) {
                return DetectionResult("Facebook Reels", contentTexts.take(3).joinToString("|"))
            }
        }
        return null
    }

    // ─────────────────────────────────────────────────────────────
    // Utility: BFS flatten with node cap
    // ─────────────────────────────────────────────────────────────
    private fun flattenNodes(
        root: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeInfo>,
        maxNodes: Int,
    ) {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && out.size < maxNodes) {
            val node = queue.removeFirst()
            out.add(node)
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
    }
}
