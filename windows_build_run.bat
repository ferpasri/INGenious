@echo off
setlocal enabledelayedexpansion

:: ════════════════════════════════════════════════════════════════
::  TESTAR-INGenious · Build & Run script
::  TFG 2025-2026 · Santiago Fuentes
::
::  USO:
::    windows_build_run.bat              → build incremental + lanzar
::    windows_build_run.bat --clean      → full clean build + lanzar
::    windows_build_run.bat --run        → lanzar SIN recompilar (más rápido)
::
::  Los datos de experimentos (mcp_all_runs.json, mcp_metrics.js)
::  se guardan en experiment_data\ y NUNCA se pierden con el rebuild.
:: ════════════════════════════════════════════════════════════════

:: ── Parsear argumentos ──────────────────────────────────────────
set DO_CLEAN=0
set SKIP_BUILD=0

for %%A in (%*) do (
    if /i "%%A"=="--clean" set DO_CLEAN=1
    if /i "%%A"=="--run"   set SKIP_BUILD=1
)

:: ── Rutas clave ─────────────────────────────────────────────────
set "ROOT_DIR=%~dp0"
set "OUTPUT_DIR=%ROOT_DIR%Dist\target"
set "DATA_DIR=%ROOT_DIR%experiment_data"

:: Carpeta estable para datos de experiments (nunca se borra con clean)
if not exist "%DATA_DIR%" (
    mkdir "%DATA_DIR%"
    echo [DATA] Carpeta de datos creada: experiment_data\
)

:: ── BUILD ───────────────────────────────────────────────────────
if "%SKIP_BUILD%"=="1" (
    echo [BUILD] Saltando compilacion (--run)
    goto :locate_zip
)

if "%DO_CLEAN%"=="1" (
    echo [BUILD] Maven CLEAN install (full rebuild)...
    call mvn clean install -U --file "%ROOT_DIR%pom.xml"
) else (
    echo [BUILD] Maven install incremental (usa --clean para full rebuild)...
    call mvn install -U --file "%ROOT_DIR%pom.xml"
)

if errorlevel 1 (
    echo.
    echo ══════════════════════════════════════════════
    echo  ERROR: El build de Maven ha fallado.
    echo  Revisa los errores arriba. Si es la primera
    echo  vez, prueba con: windows_build_run.bat --clean
    echo ══════════════════════════════════════════════
    exit /b 1
)
echo [BUILD] Build completado correctamente.

:: ── LOCALIZAR ZIP ───────────────────────────────────────────────
:locate_zip
set "zipPath="
for /f "tokens=*" %%F in ('dir /b "%OUTPUT_DIR%\ingenious-playwright-*-setup.zip" 2^>nul') do (
    set "zipPath=%OUTPUT_DIR%\%%F"
)

if not defined zipPath (
    echo.
    echo ERROR: No se encontro ningun ZIP en %OUTPUT_DIR%
    echo        Asegurate de haber compilado al menos una vez.
    exit /b 1
)
echo [ZIP] Usando: %zipPath%

:: ── PRESERVAR DATOS DE EXPERIMENTOS ─────────────────────────────
:: Antes de eliminar la carpeta extraida, guardamos los datos en experiment_data\
for /d %%D in ("%OUTPUT_DIR%\ingenious-playwright-*") do (
    if exist "%%D\Run.bat" (
        if exist "%%D\mcp_all_runs.json" (
            copy /Y "%%D\mcp_all_runs.json" "%DATA_DIR%\mcp_all_runs.json" > nul
            echo [DATA] mcp_all_runs.json preservado (historial de experimentos OK)
        )
        if exist "%%D\mcp_metrics.js" (
            copy /Y "%%D\mcp_metrics.js" "%DATA_DIR%\mcp_metrics.js" > nul
        )
        rmdir /s /q "%%D"
    )
)

:: ── EXTRACCION ──────────────────────────────────────────────────
echo [ZIP] Extrayendo...
tar -xf "%zipPath%" -C "%OUTPUT_DIR%"
if errorlevel 1 (
    echo ERROR: Fallo la extraccion del ZIP.
    exit /b 1
)

:: ── LOCALIZAR CARPETA EXTRAIDA ───────────────────────────────────
set "appDir="
for /d %%D in ("%OUTPUT_DIR%\ingenious-playwright-*") do set "appDir=%%D"

if not defined appDir (
    echo ERROR: No se encontro la carpeta extraida.
    exit /b 1
)
echo [ZIP] Extraido en: %appDir%

:: ── RESTAURAR DATOS Y COPIAR DASHBOARD ──────────────────────────
:: Restaurar historial de experimentos
if exist "%DATA_DIR%\mcp_all_runs.json" (
    copy /Y "%DATA_DIR%\mcp_all_runs.json" "%appDir%\mcp_all_runs.json" > nul
    echo [DATA] Historial de experimentos restaurado.
)
if exist "%DATA_DIR%\mcp_metrics.js" (
    copy /Y "%DATA_DIR%\mcp_metrics.js" "%appDir%\mcp_metrics.js" > nul
)

:: Copiar dashboard siempre desde la fuente del proyecto (version mas reciente)
if exist "%ROOT_DIR%testar_metrics_dashboard.html" (
    copy /Y "%ROOT_DIR%testar_metrics_dashboard.html" "%appDir%\testar_metrics_dashboard.html" > nul
    echo [DASH] Dashboard de metricas copiado.
)

:: Exportar ruta de datos estable para que Java escriba ahi directamente
set "MCP_DATA_DIR=%DATA_DIR%"

:: ── LANZAR INGENIOUS ────────────────────────────────────────────
set "runner=%appDir%\Run.bat"

if exist "%runner%" (
    echo.
    echo ══════════════════════════════════════════════
    echo  Lanzando INGenious...
    echo  Datos en: experiment_data\
    echo ══════════════════════════════════════════════
    echo.
    call "%runner%"
) else (
    echo ERROR: No se encontro el script de lanzamiento: %runner%
    exit /b 1
)

endlocal
