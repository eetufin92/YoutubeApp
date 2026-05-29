package com.eetu.youtubeapp.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Destination : NavKey {
    @Serializable
    data object Player : Destination

    @Serializable
    data object Settings : Destination

    @Serializable
    data object BrowserSettings : Destination
}
