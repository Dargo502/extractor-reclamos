package reclamos

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpRequest
import java.time.Duration

/**
 * Llama a Groq (plan gratuito, modelos abiertos) con su API compatible con OpenAI.
 * Usa el mismo prompt y el mismo esquema que ClienteClaude: solo cambia el formato del HTTP.
 */
class ClienteGroq(
    private val apiKey: String,
    private val modelo: String = "openai/gpt-oss-20b",
) : ExtractorReclamos {

    override val nombre = "Groq ($modelo)"
    private val json = ObjectMapper()
    private val esquema: JsonNode = json.readTree(Prompt.ESQUEMA)

    override fun extraer(correo: Correo): JsonNode {
        val cuerpo = json.createObjectNode().apply {
            put("model", modelo)
            put("temperature", 0)                 // misma entrada → misma salida, en lo posible
            put("max_completion_tokens", 4000)    // este modelo "razona" antes de responder y gasta tokens en eso
            putArray("messages").apply {
                addObject().put("role", "system").put("content", Prompt.SISTEMA)
                addObject().put("role", "user").put("content", Prompt.mensajeUsuario(correo))
            }
            // Salida estructurada en modo estricto: la respuesta tiene que cumplir el esquema.
            putObject("response_format").apply {
                put("type", "json_schema")
                putObject("json_schema").apply {
                    put("name", "reclamos")
                    put("strict", true)
                    set<JsonNode>("schema", esquema)
                }
            }
        }

        val solicitud = HttpRequest.newBuilder(URI.create("https://api.groq.com/openai/v1/chat/completions"))
            .header("Authorization", "Bearer $apiKey")
            .header("content-type", "application/json")
            .timeout(Duration.ofSeconds(90))
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)))
            .build()

        val raiz = json.readTree(enviarConReintentos(solicitud).body())
        val opcion = raiz.path("choices").path(0)

        // "length" = se quedó sin tokens a medio JSON.
        val finishReason = opcion.path("finish_reason").asText()
        if (finishReason == "length") error("Respuesta incompleta del modelo (finish_reason=length)")

        val texto = opcion.path("message").path("content").asText("")
        if (texto.isBlank()) error("La respuesta del modelo no trae texto")

        return json.readTree(texto)
    }
}
