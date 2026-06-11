@echo off
REM INGenious Codex CLI wrapper for Windows
REM Prompts for Codex agent settings and runs the packaged Codex helper

setlocal
set "ROOT_DIR=%~dp0"
if "%ROOT_DIR:~-1%"=="\" set "ROOT_DIR=%ROOT_DIR:~0,-1%"

pushd "%ROOT_DIR%"
node "%ROOT_DIR%\codex\ingenious-codex-cli.mjs" "%ROOT_DIR%"
popd
