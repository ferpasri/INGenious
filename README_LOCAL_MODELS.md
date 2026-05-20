# Guía: Modelos Locales con Ollama en TESTAR-INGenious

> TFG 2025-2026 · Santiago Fuentes  
> Estrategia: Split + Verbose · SUT: Parabank

Esta guía cubre todo el ciclo: instalar Ollama, descargar los modelos utilizados en el experimento, arrancarlos correctamente y configurar INGenious para usarlos.

---

## 1. Requisitos previos

| Requisito | Versión mínima | Notas |
|---|---|---|
| Sistema operativo | Windows 10/11 · macOS 12+ · Ubuntu 20.04+ | — |
| RAM | 8 GB | 16 GB recomendado para modelos 7-9B |
| VRAM (GPU) | 6 GB VRAM | Opcional; sin GPU el modelo corre en CPU (más lento) |
| Disco libre | 10 GB por modelo | Qwen 2.5:7b ≈ 4.7 GB |
| Java | 17+ | Necesario para INGenious |
| Maven | 3.9+ | Necesario para compilar INGenious |

---

## 2. Instalar Ollama

Ollama es el runtime que descarga y sirve los modelos locales. El proyecto lo usa como backend de inferencia a través de `http://localhost:11434`.

### Windows

Descarga el instalador desde la web oficial y ejecútalo. Ollama queda registrado como servicio de Windows y arranca automáticamente.

```
https://ollama.com/download/windows
```

Verifica la instalación abriendo una terminal:

```cmd
ollama --version
```

### macOS

```bash
brew install ollama
```

O descarga el `.dmg` desde `https://ollama.com/download/mac`.

### Linux (Ubuntu/Debian)

```bash
curl -fsSL https://ollama.com/install.sh | sh
```

El script instala el binario en `/usr/local/bin/ollama` y crea el servicio systemd.

---

## 3. Descargar los modelos del experimento

Una vez Ollama está instalado, descarga los modelos con `ollama pull`. No hace falta que el servidor esté arrancado manualmente en este paso; `ollama pull` lo arranca solo.

### Qwen 2.5:7b (modelo principal — provider `Qwen/Ollama`)

```bash
ollama pull qwen2.5:7b
```

Este es el modelo recomendado. Usa el endpoint `/api/chat` con soporte nativo de tool calling, lo que elimina el parsing de JSON manual y reduce alucinaciones.

```bash
# Variante más grande si tienes ≥12 GB VRAM
ollama pull qwen2.5:14b

# Qwen3 — generación más nueva con razonamiento interno
ollama pull qwen3:8b
```

### Resto de modelos usados en los experimentos

```bash
# Gemma 2:9b (provider: Local/Ollama)
ollama pull gemma2:9b

# Llama 3.1:8b (provider: Local/Ollama)
ollama pull llama3.1:8b

# Llama 3.2:3b — modelo pequeño, peor rendimiento
ollama pull llama3.2:3b

# Mistral 0.3:7b (provider: Local/Ollama)
ollama pull mistral:7b
```

Comprueba que todos están disponibles:

```bash
ollama list
```

La salida esperada es algo como:

```
NAME              ID              SIZE    MODIFIED
qwen2.5:7b        ...             4.7 GB  hace X min
gemma2:9b         ...             5.4 GB  ...
llama3.1:8b       ...             4.7 GB  ...
```

---

## 4. Arrancar el servidor Ollama

En Windows y macOS Ollama corre en background automáticamente tras la instalación. En Linux con systemd:

```bash
# Arrancar el servicio
sudo systemctl start ollama

# Habilitarlo para que arranque con el sistema
sudo systemctl enable ollama

# Ver logs si algo falla
sudo journalctl -u ollama -f
```

Verifica que el servidor responde:

```bash
curl http://localhost:11434/api/tags
```

Deberías ver un JSON con la lista de modelos disponibles. Si obtienes `Connection refused`, el servidor no está corriendo — revisa los logs.

---

## 5. Ejecutar un modelo manualmente (prueba rápida)

Antes de lanzar INGenious, comprueba que el modelo responde correctamente:

```bash
# Chat interactivo (Ctrl+D para salir)
ollama run qwen2.5:7b

# Petición directa por API (equivalente a lo que hace QwenOllamaProvider)
curl http://localhost:11434/api/chat -d '{
  "model": "qwen2.5:7b",
  "messages": [
    { "role": "user", "content": "Di hola en JSON: {\"saludo\": \"...\"}" }
  ],
  "stream": false
}'
```

La respuesta debe ser un JSON con un campo `message.content` que contiene la respuesta del modelo.

---

## 6. Configurar INGenious para usar el modelo local

Los ajustes del agente LLM se almacenan en el fichero de configuración del proyecto. El campo clave es `llmProviderName`.

### Provider `Qwen/Ollama` (recomendado para Qwen 2.5 y Qwen 3)

```json
{
  "llmProviderName": "Qwen/Ollama",
  "customApiUrl": "http://localhost:11434/api/chat",
  "openaiModel": "qwen2.5:7b",
  "maxActions": 30,
  "bddText": "..."
}
```

- Usa el endpoint `/api/chat` con tool calling nativo.
- La clase Java responsable es `QwenOllamaProvider`.
- Soporta historial de conversación nativo (sin duplicación de contexto).
- Limpia automáticamente los bloques `<think>...</think>` de Qwen3.

### Provider `Local/Ollama` (para Gemma, Llama, Mistral)

```json
{
  "llmProviderName": "Local/Ollama",
  "customApiUrl": "http://localhost:11434/api/generate",
  "openaiModel": "gemma2:9b",
  "maxActions": 30,
  "bddText": "..."
}
```

- Usa el endpoint `/api/generate` (sin tool calling nativo).
- El agent inyecta las herramientas como texto en el prompt.
- Más propenso a alucinaciones que `Qwen/Ollama`.

> **Regla práctica:** usa `Qwen/Ollama` para cualquier modelo Qwen. Para todo lo demás usa `Local/Ollama`.

---

## 7. Lanzar INGenious con el modelo local

### Compilar el proyecto (solo la primera vez o tras cambios)

```bash
cd INGenious
mvn clean install -DskipTests
```

### Arrancar el IDE

```bash
# Windows
windows_build_run.bat

# Linux / macOS
java -jar Dist/target/ingenious-playwright-*/IDE.jar
```

### Lanzar un run desde el IDE

1. Abre el proyecto Parabank en el IDE.
2. Ve a **Run → MCP Agent**.
3. Selecciona el provider `Qwen/Ollama` y el modelo `qwen2.5:7b`.
4. Elige el objetivo BDD (escenario `.feature`).
5. Pulsa **Start**. El agente enviará peticiones a Ollama en `localhost:11434`.

Al terminar el run, el agente genera automáticamente el fichero `mcp_metrics.js` en la raíz del proyecto. Abre `testar_metrics_dashboard.html` en el navegador para ver las métricas actualizadas.

---

## 8. Rendimiento esperado por modelo

Basado en los experimentos del TFG (50 runs por modelo, SUT: Parabank):

| Modelo | Provider | Success Rate | Avg Latencia | Avg Alucinaciones |
|---|---|---|---|---|
| qwen2.5:7b | Qwen/Ollama | **62%** | 3 200 ms | 2.1 |
| gemma2:9b | Local/Ollama | 66% | 4 100 ms | 1.8 |
| llama3.1:8b | Local/Ollama | 44% | 4 800 ms | 5.6 |
| mistral:7b | Local/Ollama | 12% | 3 600 ms | 9.1 |
| llama3.2:3b | Local/Ollama | 6% | 2 900 ms | 12.4 |

> La latencia varía mucho según el hardware. Los valores anteriores son con GPU (RTX 3080). En CPU espera ×3-×5 más lento.

---

## 9. Resolución de problemas frecuentes

**`Connection refused` al llamar a `localhost:11434`**  
El servidor Ollama no está corriendo. Ejecuta `ollama serve` en una terminal aparte o reinicia el servicio systemd.

**El modelo devuelve texto vacío o malformado**  
Asegúrate de que el tag del modelo coincide exactamente con el configurado (ej. `qwen2.5:7b`, no `qwen2.5`). Usa `ollama list` para verificar el nombre exacto.

**Timeout en `QwenOllamaProvider`**  
El timeout está configurado a 10 minutos por request. Si el modelo es muy lento en CPU, sube el timeout en `QwenOllamaProvider.java` (`Duration.ofMinutes(10)`) o usa un modelo más pequeño como `qwen2.5:3b`.

**`CUDA out of memory`**  
El modelo no cabe en tu VRAM. Opciones: (a) usa un modelo más pequeño (`qwen2.5:3b`), (b) fuerza CPU con `OLLAMA_NUM_GPU=0 ollama serve`, (c) usa cuantización menor con `ollama pull qwen2.5:7b-q4_0`.

**El dashboard muestra datos de muestra en lugar de datos reales**  
El fichero `mcp_metrics.js` no existe todavía. Ejecuta al menos un run completo con INGenious para que el agente lo genere. Verifica que el fichero está en el mismo directorio que `testar_metrics_dashboard.html`.

---

## 10. Referencia rápida de comandos

```bash
# Instalar Ollama (Linux)
curl -fsSL https://ollama.com/install.sh | sh

# Descargar el modelo principal del experimento
ollama pull qwen2.5:7b

# Verificar que el servidor responde
curl http://localhost:11434/api/tags

# Ver todos los modelos instalados
ollama list

# Arrancar el servidor manualmente (si no corre como servicio)
ollama serve

# Chat rápido para probar el modelo
ollama run qwen2.5:7b

# Eliminar un modelo (liberar espacio)
ollama rm llama3.2:3b
```

---

*Guía generada para el TFG "Integración de Modelos LLM Locales en TESTAR-INGenious" · UPV 2025-2026*
