package com.blindfriend.intent

import com.blindfriend.llm.GemmaInference

class IntentClassifier(private val gemma: GemmaInference) {

    private val systemPrompt = """
You are an intent classifier. Given a user request in Spanish or English,
respond ONLY with a JSON object. No prose, no explanation.

Schema:
{"mode": "NAVIGATION|FIND_OBJECT|LIGHT_CHECK|PERSON_DETECT|READ_TEXT|GENERAL", "object": "<noun or null>"}

Examples:
"ayúdame a caminar" -> {"mode":"NAVIGATION","object":null}
"encuentra mi reloj" -> {"mode":"FIND_OBJECT","object":"reloj"}
"está la luz prendida" -> {"mode":"LIGHT_CHECK","object":null}
"hay alguien en la habitación" -> {"mode":"PERSON_DETECT","object":null}
"qué dice ese letrero" -> {"mode":"READ_TEXT","object":null}
"describe lo que ves" -> {"mode":"GENERAL","object":null}
""".trimIndent()

    suspend fun classify(userSpeech: String): UserIntent {
        val prompt = "$systemPrompt\n\nUser: \"$userSpeech\"\nJSON:"
        return try {
            val response = gemma.generateText(prompt)
            parseJson(response, userSpeech)
        } catch (_: Exception) {
            UserIntent(AssistMode.GENERAL, rawRequest = userSpeech)
        }
    }

    private fun parseJson(response: String, raw: String): UserIntent {
        val jsonRegex = """\{[^}]+\}""".toRegex()
        val json = jsonRegex.find(response)?.value
            ?: return UserIntent(AssistMode.GENERAL, rawRequest = raw)

        val mode = when {
            "NAVIGATION" in json -> AssistMode.NAVIGATION
            "FIND_OBJECT" in json -> AssistMode.FIND_OBJECT
            "LIGHT_CHECK" in json -> AssistMode.LIGHT_CHECK
            "PERSON_DETECT" in json -> AssistMode.PERSON_DETECT
            "READ_TEXT" in json -> AssistMode.READ_TEXT
            else -> AssistMode.GENERAL
        }
        val target = """"object"\s*:\s*"([^"]+)"""".toRegex()
            .find(json)?.groupValues?.get(1)?.takeIf { it != "null" }

        return UserIntent(mode, target, raw)
    }
}
