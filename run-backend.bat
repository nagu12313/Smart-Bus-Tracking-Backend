@echo off
title Smart Bus Backend - Spring Boot
echo ====================================================
echo Starting Smart Bus Spring Boot Backend...
echo Connecting to Aiven Cloud MySQL with SSL...
echo ====================================================

set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot
set MVN=C:\Users\bolla\Downloads\apache-maven-3.10.0-bin\apache-maven-3.10.0\bin\mvn.cmd

cd /d "%~dp0"

:: Load local .env if it exists
if exist .env (
    for /f "usebackq tokens=1,* delims==" %%a in (".env") do (
        set "%%a=%%b"
    )
)

:: Fallback defaults if not set in .env
if "%DB_USERNAME%"=="" set DB_USERNAME=avnadmin
if "%SSL_KEYSTORE_PATH%"=="" set SSL_KEYSTORE_PATH=C:/Users/bolla/Downloads/truststore.jks
if "%SSL_KEYSTORE_PASSWORD%"=="" set SSL_KEYSTORE_PASSWORD=changeit
if "%JWT_SECRET%"=="" set JWT_SECRET=SmartBusEduTrackSecretKey2024VeryLongSecretForHMACSHA256Algorithm
if "%CORS_ORIGINS%"=="" set CORS_ORIGINS=http://localhost:5173,http://localhost:5174,http://localhost:3000,https://*.vercel.app

"%MVN%" spring-boot:run
pause
