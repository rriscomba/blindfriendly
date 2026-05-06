package com.blindfriend.intent

data class UserIntent(
    val mode: AssistMode,
    val targetObject: String? = null,
    val rawRequest: String = ""
)
