@echo off
@rem Maven Wrapper script for Windows
@rem Uses maven-wrapper.jar to download and run Maven

setlocal

set BASEDIR=%~dp0
@rem Remove trailing backslash if present
if "%BASEDIR:~-1%"=="\" set BASEDIR=%BASEDIR:~0,-1%

set WRAPPER_JAR=%BASEDIR%\.mvn\wrapper\maven-wrapper.jar

if not exist "%WRAPPER_JAR%" (
    echo Error: maven-wrapper.jar not found at %WRAPPER_JAR%
    echo Please ensure the .mvn\wrapper directory exists with maven-wrapper.jar
    exit /b 1
)

if defined JAVA_HOME (
    set "JAVACMD=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVACMD=java.exe"
)

set MAVEN_PROJECTBASEDIR=%BASEDIR%

"%JAVACMD%" %MAVEN_OPTS% -Dmaven.multiModuleProjectDirectory="%BASEDIR%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
