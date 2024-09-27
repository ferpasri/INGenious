@echo off

:: Create clean distribution
call mvn clean install --file pom.xml

:: This is the output target app
set zipPath=Dist\target\ingenious-playwright-1.0-setup.zip
set outputDir=Dist\target

:: Unzip using tar command
tar -xf %zipPath% -C %outputDir%

set runner=Dist\target\ingenious-playwright-1.0\Run.bat
call %runner%