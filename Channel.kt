package com.optis.tvradyopro.model

data class Channel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String?,
    val originalGroup: String,
    val smartCategory: String,
    val isRadio: Boolean,
    val userAgent: String,
    val referer: String,
    val origin: String
)
