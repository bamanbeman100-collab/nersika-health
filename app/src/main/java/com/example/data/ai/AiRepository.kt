package com.example.data.ai

import kotlinx.coroutines.CancellationException

interface AiRepository {
    suspend fun generateClinicalAssistance(request: AiClinicalRequest): AiClinicalResponse
}

class GeminiAiRepository(
    private val gateway: GeminiModelGateway = FirebaseGeminiModelGateway()
) : AiRepository {

    override suspend fun generateClinicalAssistance(request: AiClinicalRequest): AiClinicalResponse {
        val accessDenial = AiAccessPolicy.evaluate(
            actor = request.actor,
            patient = request.patient,
            taskType = request.taskType
        )
        if (accessDenial != null) {
            throw AiAccessDeniedException(
                reason = accessDenial,
                message = "AI request denied by policy: ${accessDenial.name}"
            )
        }

        val builtPrompt = AiPromptBuilder.build(request)

        return try {
            val rawJson = gateway.generateClinicalJson(
                systemInstruction = builtPrompt.systemInstruction,
                prompt = builtPrompt.userPrompt,
                image = request.documentBitmap
            )
            AiOutputValidator.parseAndValidate(
                rawJson = rawJson,
                request = request,
                promptContext = builtPrompt
            )
        } catch (e: CancellationException) {
            throw e
        }
    }
}
