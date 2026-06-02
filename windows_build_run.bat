@echo off
setlocal

set "ROOT_DIR=%~dp0"
pushd "%ROOT_DIR%"

echo [1/5] Building ingenious-api...
call mvn clean install -U --file ingenious-api\pom.xml
if errorlevel 1 (
    echo ERROR: ingenious-api build failed.
    popd
    exit /b 1
)

echo [2/5] Building full distribution...
call mvn clean install -U --file pom.xml
if errorlevel 1 (
    echo ERROR: root build failed.
    popd
    exit /b 1
)

set "OUTPUT_DIR=Dist\target"
set "ZIP_PATH="

echo [3/5] Locating packaged ZIP...
for /f %%F in ('dir /b /o:-d "%OUTPUT_DIR%\ingenious-playwright-*-setup.zip"') do (
    set "ZIP_PATH=%OUTPUT_DIR%\%%F"
    goto :zip_found
)

:zip_found
if not defined ZIP_PATH (
    echo ERROR: No ZIP file found in %OUTPUT_DIR%
    popd
    exit /b 1
)

echo [4/5] Extracting %ZIP_PATH%...
tar -xf "%ZIP_PATH%" -C "%OUTPUT_DIR%"
if errorlevel 1 (
    echo ERROR: Failed to extract %ZIP_PATH%
    popd
    exit /b 1
)

set "APP_DIR="
for /d %%D in ("%OUTPUT_DIR%\ingenious-playwright-*") do (
    if /i not "%%~nxD"=="setup" (
        set "APP_DIR=%%D"
    )
)

if not defined APP_DIR (
    echo ERROR: Extraction failed or app folder not found.
    popd
    exit /b 1
)

set "RUNNER=%APP_DIR%\ingenious.bat"

echo [5/5] Launching %RUNNER%...
if exist "%RUNNER%" (
    call "%RUNNER%"
) else (
    echo ERROR: Runner script not found: %RUNNER%
    popd
    exit /b 1
)

popd
endlocal
