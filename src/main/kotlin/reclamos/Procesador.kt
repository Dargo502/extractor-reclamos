package reclamos

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.File
import java.time.LocalDate

/** Une las piezas: leer correo → extraer con el modelo → validar → guardar. */
class Procesador(private val extractor: ExtractorReclamos) {

    fun procesar(correo: Correo): Resultado = try {
        val respuesta = extractor.extraer(correo)
        val reclamos = respuesta.path("reclamos").map { aReclamo(it) }
        Resultado(
            archivo = correo.archivo,
            esReclamo = respuesta.path("es_reclamo").asBoolean(false),
            reclamos = reclamos.map { Validador.revisar(it, correo) },
        )
    } catch (e: Exception) {
        // Un correo que falla no detiene a los demás; queda registrado con su error.
        Resultado(correo.archivo, esReclamo = false, reclamos = emptyList(), error = e.message)
    }

    private fun aReclamo(n: JsonNode) = Reclamo(
        unidad = n.textoONulo("unidad")?.uppercase(),
        placas = n.textoONulo("placas")?.uppercase(),
        tag = n.textoONulo("tag"),
        caseta = n.textoONulo("caseta"),
        fechaInicio = n.textoONulo("fecha_inicio"),
        fechaFin = n.textoONulo("fecha_fin"),
        monto = n.path("monto").takeIf { it.isNumber }?.asDouble(),
        motivo = n.path("motivo").asText(""),
        cita = n.path("cita").asText(""),
        dudas = n.textoONulo("dudas"),
    )
}

private fun JsonNode.textoONulo(campo: String): String? =
    path(campo).takeIf { it.isTextual }?.asText()?.trim()?.takeIf { it.isNotEmpty() }

/** Lee un archivo .txt de correo. Necesita una línea "Fecha: YYYY-MM-DD" en el encabezado. */
fun leerCorreo(archivo: File): Correo {
    val texto = archivo.readText()
    val lineaFecha = texto.lines().firstOrNull { it.startsWith("Fecha:") }
        ?: error("${archivo.name}: falta la línea 'Fecha: YYYY-MM-DD'")
    return Correo(archivo.name, LocalDate.parse(lineaFecha.removePrefix("Fecha:").trim()), texto)
}

/** Guarda los resultados en JSON (para el evaluador) y en CSV (para abrir en Excel). */
object Salida {
    private val json = ObjectMapper()

    fun guardar(resultados: List<Resultado>, carpeta: File) {
        carpeta.mkdirs()
        val arreglo = json.createArrayNode()
        resultados.forEach { arreglo.add(aJson(it)) }
        json.writerWithDefaultPrettyPrinter().writeValue(File(carpeta, "resultados.json"), arreglo)
        File(carpeta, "resultados.csv").writeText(aCsv(resultados), Charsets.UTF_8)
    }

    private fun aJson(r: Resultado): ObjectNode = json.createObjectNode().apply {
        put("archivo", r.archivo)
        put("es_reclamo", r.esReclamo)
        put("error", r.error)
        val lista = putArray("reclamos")
        r.reclamos.forEach { rev ->
            val c = rev.reclamo
            lista.addObject().apply {
                put("estado", rev.estado.name)
                put("unidad", c.unidad)
                put("placas", c.placas)
                put("tag", c.tag)
                put("caseta", c.caseta)
                put("fecha_inicio", c.fechaInicio)
                put("fecha_fin", c.fechaFin)
                put("monto", c.monto)
                put("motivo", c.motivo)
                put("cita", c.cita)
                put("dudas", c.dudas)
                val motivos = putArray("motivos_revision")
                rev.motivosRevision.forEach { motivos.add(it) }
            }
        }
    }

    private fun aCsv(resultados: List<Resultado>): String {
        val encabezado = listOf("archivo", "estado", "unidad", "placas", "tag", "caseta",
            "fecha_inicio", "fecha_fin", "monto", "motivo", "motivos_revision")
        val filas = resultados.flatMap { r ->
            if (r.error != null) listOf(listOf(r.archivo, "ERROR", "", "", "", "", "", "", "", "", r.error))
            else r.reclamos.map { rev ->
                val c = rev.reclamo
                listOf(r.archivo, rev.estado.name, c.unidad, c.placas, c.tag, c.caseta,
                    c.fechaInicio, c.fechaFin, c.monto?.toString(), c.motivo,
                    rev.motivosRevision.joinToString(" | "))
            }
        }
        // "﻿" (BOM) hace que Excel lea bien los acentos.
        return "﻿" + (listOf(encabezado) + filas).joinToString("\n") { fila ->
            fila.joinToString(",") { celda -> "\"" + (celda ?: "").replace("\"", "\"\"") + "\"" }
        } + "\n"
    }
}
