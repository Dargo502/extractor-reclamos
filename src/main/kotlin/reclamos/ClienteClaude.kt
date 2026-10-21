package reclamos

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpRequest
import java.time.Duration

/**
 * Cualquier cosa que sepa convertir un correo en el JSON del esquema.
 * Es una interfaz para poder cambiar de modelo o de proveedor (Claude, Groq...),
 * o usar uno simulado en pruebas, sin tocar el resto del programa.
 */
interface ExtractorReclamos {
    val nombre: String
    fun extraer(correo: Correo): JsonNode
}

/** Llama a la API de Claude directamente por HTTP (sin SDK), con salida estructurada. */
class ClienteClaude(
    private val apiKey: String,
    private val modelo: String = "claude-haiku-5-5",
) : ExtractorReclamos {

    override val nombre = "Claude ($modelo)"
    private val json = ObjectMapper()
    private val esquema: JsonNode = json.readTree(Prompt.ESQUEMA)

    override fun extraer(correo: Correo): JsonNode {
        val cuerpo = json.createObjectNode().apply {
            put("model", modelo)
            put("max_tokens", 2000)
            put("system", Prompt.SISTEMA)
            putArray("messages").addObject().apply {
                put("role", "user")
                put("content", Prompt.mensajeUsuario(correo))
            }
            // Salida estructurada: la respuesta tiene que cumplir el esquema JSON.
            putObject("output_config").putObject("format").apply {
                put("type", "json_schema")
                set<JsonNode>("schema", esquema)
            }
        }

        val solicitud = HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/messages"))
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .timeout(Duration.ofSeconds(90))
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)))
            .build()

        val raiz = json.readTree(enviarConReintentos(solicitud).body())

        // Si el modelo se cortó o se negó, el JSON puede venir incompleto: mejor fallar claro.
        val stopReason = raiz.path("stop_reason").asText()
        if (stopReason == "max_tokens" || stopReason == "refusal") {
            error("Respuesta incompleta del modelo (stop_reason=$stopReason)")
        }

        val texto = raiz.path("content")
            .firstOrNull { it.path("type").asText() == "text" }
            ?.path("text")?.asText()
            ?: error("La respuesta del modelo no trae texto")

        return json.readTree(texto)
    }
}
