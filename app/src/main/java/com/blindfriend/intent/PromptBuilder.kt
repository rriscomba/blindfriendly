package com.blindfriend.intent

object PromptBuilder {

    fun build(intent: UserIntent): String = when (intent.mode) {

        AssistMode.NAVIGATION ->
            "Eres guía de una persona ciega caminando. Analiza esta imagen. " +
            "En máximo 2 oraciones cortas: describe el camino y obstáculos inmediatos. " +
            "Sé específico con distancias: 'a 1 metro', 'a tu izquierda'. Sin saludos."

        AssistMode.FIND_OBJECT -> {
            val obj = intent.targetObject ?: "el objeto"
            "Ayudas a una persona ciega a encontrar $obj. Mira la imagen completa. " +
            "Si lo ves: di exactamente dónde está respecto a la cámara (izquierda/derecha/centro, cerca/lejos). " +
            "Si no está visible: di 'no veo $obj' y sugiere mirar otra dirección. Sé breve."
        }

        AssistMode.LIGHT_CHECK ->
            "Analiza la iluminación de esta imagen. En una oración: ¿está la luz prendida o apagada? " +
            "¿Es brillante, tenue u oscuro? Sé directo."

        AssistMode.PERSON_DETECT ->
            "Mira esta imagen. ¿Cuántas personas son visibles? ¿Dónde están (izquierda/derecha/centro, cerca/lejos)? " +
            "Si no hay personas: di 'no veo a nadie'. Una oración por persona máximo."

        AssistMode.READ_TEXT ->
            "Lee todo el texto visible en esta imagen. Transcríbelo exactamente como aparece, en orden de lectura. " +
            "Si no hay texto: di 'no veo texto legible'."

        AssistMode.GENERAL ->
            "Describe lo que ves en máximo 3 oraciones. Enfócate en detalles útiles para una persona ciega: " +
            "objetos, personas, distribución espacial. Sé directo y específico."
    }

    fun confirmationMessage(intent: UserIntent): String = when (intent.mode) {
        AssistMode.NAVIGATION -> "Analizando el camino"
        AssistMode.FIND_OBJECT -> "Buscando ${intent.targetObject ?: "el objeto"}"
        AssistMode.LIGHT_CHECK -> "Revisando la luz"
        AssistMode.PERSON_DETECT -> "Escaneando la habitación"
        AssistMode.READ_TEXT -> "Leyendo el texto"
        AssistMode.GENERAL -> "Analizando"
    }

    fun isContinuous(mode: AssistMode) = mode == AssistMode.NAVIGATION
}
