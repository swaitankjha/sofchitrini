package com.jhacode.chitrini.update.model

import com.google.gson.annotations.SerializedName

/**
 * Data model for the version.json file hosted on GitHub/HTTPS server.
 */
data class VersionInfo(
    @SerializedName("versionCode") val versionCode: Float,
    @SerializedName("versionName") val versionName: String,
    @SerializedName("mandatory") val mandatory: Boolean,
    @SerializedName("downloadUrl") val downloadUrl: String,
    @SerializedName("changelog") val changelog: List<String>
)
