# Extractor de reclamos de casetas

Convierte correos de clientes que reclaman cobros de casetas en datos estructurados (unidad, placas, tag, caseta, fechas y monto), listos para cruzarse contra GPS. Cada reclamo sale marcado como **LISTO** o **REVISAR**, con el motivo.

Está escrito en Kotlin y funciona con dos proveedores de LLM con salida estructurada: **Claude** (Anthropic) o **Groq** (modelos abiertos, plan gratuito).

## El problema

En una empresa de pagos para flotillas, los clientes reclaman cobros de casetas por correo. Algunos mandan un Excel ordenado, pero otros escriben cosas como *"revisen los cargos de la T-145 del 10 al 17, estuvo en el taller"*. Alguien tiene que leer cada correo y pasarlo a mano a una tabla antes de poder revisarlo. Este programa automatiza ese primer paso.

## Cómo funciona

```
correo .txt → LLM (salida estructurada)   → Validador (reglas en código) → resultados.csv / .json
                                                                         ↓
                                              Evaluador ← esperado.json (respuestas conocidas)
```

| Archivo | Qué hace |
|---|---|
| `Prompt.kt` | Instrucciones para el modelo y el esquema JSON que debe cumplir la respuesta. |
| `ClienteClaude.kt` | Define la interfaz `ExtractorReclamos` y llama a la API de Claude por HTTP. |
| `ClienteGroq.kt` | La misma extracción con Groq (API compatible con OpenAI). |
| `Http.kt` | Envío HTTP con reintentos para errores temporales (429, 5xx), compartido por ambos clientes. |
| `Validador.kt` | Reglas de negocio que **no** dependen del modelo. |
| `Procesador.kt` | Une las piezas, lee correos y guarda CSV/JSON. |
| `Evaluador.kt` | Compara resultados contra respuestas conocidas y reporta aciertos por campo. |

## Decisiones de diseño

- **Salida estructurada con esquema JSON.** La API obliga a que la respuesta tenga exactamente los campos definidos. No hay que interpretar texto libre.
- **No confiar ciegamente en el modelo.** El modelo extrae, pero las reglas se revisan en código:
  - Debe venir al menos un identificador del camión: unidad, placas o tag.
  - Las fechas deben ser válidas y no pueden ser posteriores a la fecha del correo ni de hace más de 90 días.
  - El monto tiene que ser positivo.
  - **La cita textual debe existir en el correo.** El modelo tiene que copiar el fragmento que respalda cada reclamo. Si ese fragmento no aparece en el correo original, el dato pudo ser inventado y el reclamo va a revisión.
- **Persona en el circuito.** Lo dudoso no se descarta ni se adivina: queda en REVISAR con el motivo. El objetivo es que una persona revise 20 casos en vez de 200, no que nadie revise nada.
- **Evaluación con casos conocidos.** `esperado.json` tiene las respuestas correctas de los correos de ejemplo. Antes de cambiar el prompt o el modelo, se corre el evaluador para comprobar que no empeoró.
- **Interfaz `ExtractorReclamos`.** El resto del programa no sabe qué modelo se usa. Agregar Groq fue un archivo nuevo, sin tocar el validador, la salida ni el evaluador. También permite usar respuestas simuladas en pruebas.
- **Un error no detiene todo.** Si un correo falla, queda registrado como ERROR y los demás se siguen procesando.

## Cómo correrlo

Requisitos: JDK 17 o superior para correr Gradle (el JDK 21 del proyecto se descarga solo) y una API key de uno de estos proveedores:

- **Groq (gratis, sin tarjeta):** [console.groq.com/keys](https://console.groq.com/keys) → variable `GROQ_API_KEY`. Usa el modelo `openai/gpt-oss-20b`.
- **Claude:** [console.anthropic.com](https://console.anthropic.com) → variable `ANTHROPIC_API_KEY`. Usa `claude-haiku-5-5`.

Si están las dos, usa Claude.

**Con IntelliJ IDEA:** abrir la carpeta del proyecto, esperar a que Gradle sincronice y correr `Main.kt` con los argumentos `extraer`. La API key va en las variables de entorno de la configuración de ejecución (*Run → Edit Configurations → Environment variables*).

**Desde la terminal:**

```bash
# Windows (PowerShell)
$env:GROQ_API_KEY="gsk_..."
.\gradlew.bat run --args="extraer"
.\gradlew.bat run --args="evaluar"

# macOS / Linux
export GROQ_API_KEY="gsk_..."
./gradlew run --args="extraer"
./gradlew run --args="evaluar"
```

`extraer` lee los `.txt` de `ejemplos/` y escribe en `salida/` un `resultados.csv`, que se abre en Excel, y un `resultados.json`. `evaluar` compara ese JSON contra `ejemplos/esperado.json`.

Si en la consola de Windows los acentos se ven raros, ejecuta `chcp 65001` antes.

Cada correo necesita una línea `Fecha: YYYY-MM-DD` en el encabezado. Con ella se resuelven fechas relativas como "el sábado pasado".

## Casos de ejemplo

| Correo | Qué prueba |
|---|---|
| `01_taller` | Caso simple: unidad y rango de fechas. |
| `02_finanzas_monto` | Correo formal con tag, caseta y monto. |
| `03_dos_unidades` | Un correo con dos camiones debe dar dos reclamos. |
| `04_placas_fecha_relativa` | Solo placas y "el sábado pasado". El estado no se evalúa porque es razonable que el modelo marque duda. |
| `05_sin_identificador` | "El camión de Don Chuy" no identifica al camión y debe ir a REVISAR. |
| `06_no_es_reclamo` | Pide una factura y no debe generar reclamos. |
| `07_fecha_futura` | Fechas posteriores al correo, probablemente un error del cliente. El validador debe atraparlo. |

## Limitaciones conocidas

- Solo lee correos en `.txt`. Todavía no lee PDF, Excel ni capturas de pantalla.
- El evaluador empareja los reclamos por orden. Si el modelo los devuelve en otro orden, cuenta como diferencia.
- El formato de unidad `T-###` está fijo para este cliente. Otro cliente necesitaría otra regla.

## Próximos pasos

1. Leer reclamos en Excel, que son la mayoría, y PDF.
2. Cruzar cada reclamo contra el GPS para marcarlo como *cargo correcto*, *cargo incorrecto con evidencia* o *no concluyente*.
3. Detectar cambios de tag sin avisar. Si el camión reclamado estaba lejos pero otro camión de la flota sí pasó por esa caseta a esa hora, probablemente el tag se movió.
4. Darle al cliente una forma de avisar cambios de tag, por ejemplo por WhatsApp, para que el problema no aparezca.
