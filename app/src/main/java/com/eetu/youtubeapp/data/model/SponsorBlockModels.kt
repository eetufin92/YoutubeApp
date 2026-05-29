package com.eetu.youtubeapp.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class VideoSegmentsResponse(
    val videoID: String,
    val hash: String? = null,
    val segments: List<Segment>
)

@JsonClass(generateAdapter = true)
data class Segment(
    val segment: List<Double>, // [start, end]
    val category: String,
    val actionType: String,
    @Json(name = "UUID") val uuid: String,
    val votes: Int = 0
) {
    val start: Double get() = segment.getOrNull(0) ?: 0.0
    val end: Double get() = segment.getOrNull(1) ?: 0.0
}
