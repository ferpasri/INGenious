@echo off
setlocal

set "ROOT_DIR=%~dp0"
set "MODE=%~1"

pushd "%ROOT_DIR%"

echo [1/2] Building ingenious-api...
call mvn clean install -U --file ingenious-api\pom.xml
if errorlevel 1 (
    echo ERROR: ingenious-api build failed.
    popd
    exit /b 1
)

echo [2/2] Building full distribution with Codex runtime dependencies...
call mvn clean install -U -Pnpm-install --file pom.xml
if errorlevel 1 (
    echo ERROR: incremental root build failed.
    popd
    exit /b 1
)

popd
endlocal
