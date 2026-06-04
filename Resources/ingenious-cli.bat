@echo off
REM INGenious CLI wrapper script for Windows
REM Usage: ingenious-cli.bat <command> [options]

setlocal
set ROOT_DIR=%~dp0
set ENGINE_DIR=%ROOT_DIR%Engine
set CLI_CLASSPATH=%ROOT_DIR%ingenious-ide-${project.version}.jar;%ROOT_DIR%lib\*;%ENGINE_DIR%\lib\*

pushd "%ROOT_DIR%"
java -cp "%CLI_CLASSPATH%" com.ing.engine.core.Control %*
popd
