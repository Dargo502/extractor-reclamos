package reclamos

import java.io.File
import kotlin.system.exitProcess

private const val USO = """
Uso:
  extraer [carpeta_correos] [carpeta_salida]   Procesa los .txt con Claude (por defecto: ejemplos salida)
  evaluar [esperado.json] [resultados.json]    Compara resultados contra lo esperado

Para 'extraer' necesita ANTHROPIC_API_KEY (Claude) o GROQ_API_KEY (Groq, gratis).
"""

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "extraer" -> extraer(
            carpetaCorreos = File(args.getOrElse(1) { "ejemplos" }),
            carpetaSalida = File(args.getOrElse(2) { "salida" }),
        )
        "evaluar" -> Evaluador.evaluar(
            archivoEsperado = File(args.getOrElse(1) { "ejemplos/esperado.json" }),
            archivoResultados = File(args.getOrElse(2) { "salida/resultados.json" }),
        )
        else -> println(USO.trim())
    }
}

fun extraer(carpetaCorreos: File, carpetaSalida: File, extractor: ExtractorReclamos? = null) {
    val archivos = carpetaCorreos.listFiles { f -> f.extension == "txt" }?.sortedBy { it.name }.orEmpty()
    if (archivos.isEmpty()) {
        println("No hay archivos .txt en ${carpetaCorreos.path}")
        exitProcess(1)
    }

    val procesador = Procesador(extractor ?: clienteDesdeEntorno())
    val resultados = archivos.map { archivo ->
        val resultado = runCatching { procesador.procesar(leerCorreo(archivo)) }
            .getOrElse { Resultado(archivo.name, false, emptyList(), it.message) }
        println(resumen(resultado))
        resultado
    }

    Salida.guardar(resultados, carpetaSalida)

    val todos = resultados.flatMap { it.reclamos }
    val listos = todos.count { it.estado == Estado.LISTO }
    println("\n${resultados.size} correos · ${todos.size} reclamos · $listos listos · " +
        "${todos.size - listos} a revisar · ${resultados.count { it.error != null }} con error")
    println("Guardado en ${carpetaSalida.path}/resultados.csv y resultados.json")
}

/** Usa Claude si hay ANTHROPIC_API_KEY; si no, Groq si hay GROQ_API_KEY. */
private fun clienteDesdeEntorno(): ExtractorReclamos {
    val claude = System.getenv("ANTHROPIC_API_KEY")
    val groq = System.getenv("GROQ_API_KEY")
    val cliente = when {
        !claude.isNullOrBlank() -> ClienteClaude(claude)
        !groq.isNullOrBlank() -> ClienteGroq(groq)
        else -> {
            println("Falta una API key: define ANTHROPIC_API_KEY o GROQ_API_KEY.")
            exitProcess(1)
        }
    }
    println("Usando ${cliente.nombre}\n")
    return cliente
}

private fun resumen(r: Resultado): String = when {
    r.error != null -> "✗ ${r.archivo}: ERROR — ${r.error}"
    !r.esReclamo -> "· ${r.archivo}: no es un reclamo"
    else -> "✓ ${r.archivo}: " + r.reclamos.joinToString("; ") { rev ->
        val quien = rev.reclamo.unidad ?: rev.reclamo.placas ?: rev.reclamo.tag ?: "¿camión?"
        if (rev.estado == Estado.LISTO) "$quien LISTO"
        else "$quien REVISAR (${rev.motivosRevision.joinToString("; ")})"
    }
}
