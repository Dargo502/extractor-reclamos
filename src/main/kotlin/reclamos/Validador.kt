package reclamos

import java.time.LocalDate

/**
 * Reglas de negocio que se revisan en código, sin confiar en el modelo.
 * Si cualquier regla falla, el reclamo queda en REVISAR para que lo vea una persona.
 */
object Validador {

    private val FORMATO_UNIDAD = Regex("""^T-\d+$""")
    private const val DIAS_MAXIMOS = 90L

    fun revisar(reclamo: Reclamo, correo: Correo): ReclamoRevisado {
        val motivos = mutableListOf<String>()

        if (reclamo.unidad == null && reclamo.placas == null && reclamo.tag == null) {
            motivos += "No identifica el camión (sin unidad, placas ni tag)"
        }
        if (reclamo.unidad != null && !FORMATO_UNIDAD.matches(reclamo.unidad)) {
            motivos += "Unidad con formato desconocido: ${reclamo.unidad}"
        }

        val inicio = leerFecha(reclamo.fechaInicio)
        val fin = leerFecha(reclamo.fechaFin)
        if (inicio == null || fin == null) {
            motivos += "Fechas faltantes o inválidas"
        } else {
            if (inicio.isAfter(fin)) motivos += "La fecha de inicio es posterior a la de fin"
            if (fin.isAfter(correo.fechaRecepcion)) motivos += "Fecha posterior a la recepción del correo"
            if (inicio.isBefore(correo.fechaRecepcion.minusDays(DIAS_MAXIMOS))) {
                motivos += "Fecha de hace más de $DIAS_MAXIMOS días"
            }
        }

        if (reclamo.monto != null && reclamo.monto <= 0) motivos += "Monto no válido: ${reclamo.monto}"

        // Defensa contra datos inventados: la cita tiene que existir en el correo original.
        if (!aparece(reclamo.cita, correo.texto)) {
            motivos += "La cita no aparece en el correo (posible dato inventado)"
        }

        reclamo.dudas?.let { motivos += "El modelo reportó dudas: $it" }

        val estado = if (motivos.isEmpty()) Estado.LISTO else Estado.REVISAR
        return ReclamoRevisado(reclamo, estado, motivos)
    }

    private fun leerFecha(texto: String?): LocalDate? =
        texto?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Compara ignorando mayúsculas y espacios de más (saltos de línea, dobles espacios). */
    private fun aparece(cita: String, texto: String): Boolean {
        fun normalizar(t: String) = t.lowercase().replace(Regex("\\s+"), " ").trim()
        return cita.isNotBlank() && normalizar(texto).contains(normalizar(cita))
    }
}
