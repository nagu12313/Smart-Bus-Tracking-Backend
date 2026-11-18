@echo off
title Smart Bus Backend - Spring Boot
echo ====================================================
echo Starting Smart Bus Spring Boot Backend...
echo Connecting to Aiven Cloud MySQL with SSL...
echo ====================================================

cd /d "%~dp0"

:: Ensure Java 17 is accessible
if exist "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
    set "PATH=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin;%PATH%"
) else if exist "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.1-hotspot\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.1-hotspot"
    set "PATH=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.1-hotspot\bin;%PATH%"
)

:: Verify java is working
java -version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Java 17 was not found in PATH or at %JAVA_HOME%.
    echo Please ensure Java 17 is installed and added to PATH.
    pause
    exit /b 1
)

:: Load local .env if it exists (skip comments starting with #)
if exist .env (
    for /f "usebackq eol=# tokens=1,* delims==" %%a in (".env") do (
        set "%%a=%%b"
    )
)

:: Environment variables are loaded from .env above
if "%CORS_ORIGINS%"=="" set "CORS_ORIGINS=http://localhost:5173,http://localhost:5174,http://localhost:3000,https://*.vercel.app"

:: Check if pre-built Spring Boot JAR exists (fastest & reliable)
if exist "target\smart-bus-backend-1.0.0.jar" (
    echo [OK] Launching Spring Boot Backend from JAR...
    echo Backend will be available at: http://localhost:8080
    echo ====================================================
    java -jar target\smart-bus-backend-1.0.0.jar
    goto done
)

:: Check if Maven is available in PATH
where mvn >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo [OK] Launching with Maven spring-boot:run...
    mvn spring-boot:run
    goto done
)

:: Check custom Maven path if present
if exist "C:\Users\bolla\Downloads\apache-maven-3.10.0-bin\apache-maven-3.10.0\bin\mvn.cmd" (
    echo [OK] Launching with custom Maven...
    "C:\Users\bolla\Downloads\apache-maven-3.10.0-bin\apache-maven-3.10.0\bin\mvn.cmd" spring-boot:run
    goto done
)

echo [ERROR] Neither target\smart-bus-backend-1.0.0.jar nor Maven was found.
echo Please ensure Maven or the packaged JAR is present in the backend directory.

:done
pause
