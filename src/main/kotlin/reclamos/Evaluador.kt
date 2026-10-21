package reclamos

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File

/**
 * Compara lo que sacó el programa contra respuestas que ya sabemos correctas (esperado.json).
 * Es la forma de saber si un cambio en el prompt o en el modelo mejoró o empeoró las cosas.
 * Solo compara los campos que aparecen en esperado.json.
 */
object Evaluador {

    private val CAMPOS_TEXTO = setOf("unidad", "placas", "tag", "caseta")

    fun evaluar(archivoEsperado: File, archivoResultados: File) {
        val json = ObjectMapper()
        val esperado = json.readTree(archivoEsperado)
        val obtenidos = json.readTree(archivoResultados).associateBy { it.path("archivo").asText() }

        val aciertos = mutableMapOf<String, Int>()
        val totales = mutableMapOf<String, Int>()
        val diferencias = mutableListOf<String>()

        fun anotar(campo: String, ok: Boolean, detalle: String) {
            totales.merge(campo, 1, Int::plus)
            if (ok) aciertos.merge(campo, 1, Int::plus) else diferencias += detalle
        }

        esperado.fields().forEach { (archivo, esp) ->
            val obt = obtenidos[archivo]
            if (obt == null) {
                anotar("correo procesado", false, "$archivo: no aparece en los resultados")
                return@forEach
            }
            anotar("correo procesado", obt.path("error").isNull, "$archivo: error → ${obt.path("error").asText()}")

            val esReclamoEsp = esp.path("es_reclamo").asBoolean()
            val esReclamoObt = obt.path("es_reclamo").asBoolean()
            anotar("es_reclamo", esReclamoEsp == esReclamoObt,
                "$archivo: es_reclamo esperado $esReclamoEsp, obtenido $esReclamoObt")

            val reclamosEsp = esp.path("reclamos").toList()
            val reclamosObt = obt.path("reclamos").toList()
            anotar("cantidad de reclamos", reclamosEsp.size == reclamosObt.size,
                "$archivo: esperaba ${reclamosEsp.size} reclamo(s), obtuvo ${reclamosObt.size}")

            // Emparejamos por posición: el primer reclamo esperado con el primero obtenido, etc.
            reclamosEsp.zip(reclamosObt).forEachIndexed { i, (e, o) ->
                e.fields().forEach { (campo, valorEsp) ->
                    val valorObt = o.path(campo)
                    anotar(campo, iguales(campo, valorEsp, valorObt),
                        "$archivo reclamo ${i + 1}: $campo esperado ${valorEsp.asText()}, obtenido ${valorObt.asText()}")
                }
            }
        }

        println("\n=== Evaluación (${esperado.size()} correos) ===")
        totales.forEach { (campo, total) ->
            val ok = aciertos[campo] ?: 0
            val marca = if (ok == total) "✓" else "✗"
            println("  $marca ${campo.padEnd(22)} $ok/$total")
        }
        if (diferencias.isEmpty()) {
            println("\nSin diferencias.")
        } else {
            println("\nDiferencias:")
            diferencias.forEach { println("  - $it") }
        }
    }

    private fun iguales(campo: String, esperado: JsonNode, obtenido: JsonNode): Boolean = when {
        esperado.isNull -> obtenido.isNull || obtenido.isMissingNode
        esperado.isNumber -> obtenido.isNumber && kotlin.math.abs(esperado.asDouble() - obtenido.asDouble()) < 0.01
        campo in CAMPOS_TEXTO -> normalizar(esperado.asText()) == normalizar(obtenido.asText())
        else -> esperado.asText() == obtenido.asText()
    }

    /** "Caseta Palmillas" == "palmillas", "45-AK-7J" == "45AK7J". */
    private fun normalizar(t: String) =
        t.uppercase().removePrefix("CASETA").replace(Regex("[^A-Z0-9ÁÉÍÓÚÑ]"), "")
}
