# ============================================================
# SSL Truststore Setup Script for Aiven Cloud MySQL
# Run this ONCE before starting the backend for the first time
# ============================================================
# This script converts the Aiven ca.pem certificate into a
# Java KeyStore (.jks) file that JDBC/MySQL Connector uses
# for SSL/TLS certificate verification.
# ============================================================

Write-Host "======================================================" -ForegroundColor Cyan
Write-Host "  Smart Bus Tracking — SSL Truststore Setup" -ForegroundColor Cyan
Write-Host "======================================================" -ForegroundColor Cyan
Write-Host ""

# Source CA cert from Aiven dashboard download
$CA_CERT = "C:\Users\bolla\Downloads\ca.pem"
$TRUSTSTORE = "C:\Users\bolla\Downloads\truststore.jks"
$TRUSTSTORE_PASSWORD = "changeit"
$ALIAS = "aiven-mysql-ca"

# Check if ca.pem exists
if (-not (Test-Path $CA_CERT)) {
    Write-Host "ERROR: ca.pem not found at $CA_CERT" -ForegroundColor Red
    Write-Host "Download it from: Aiven Console -> MySQL Service -> Overview -> Download CA cert" -ForegroundColor Yellow
    exit 1
}

Write-Host "Found ca.pem: $CA_CERT" -ForegroundColor Green

# Check if keytool is available
try {
    $keytoolVersion = & keytool -help 2>&1 | Select-Object -First 1
    Write-Host "keytool found: OK" -ForegroundColor Green
} catch {
    Write-Host "ERROR: keytool not found. Make sure Java JDK is installed and JAVA_HOME is set." -ForegroundColor Red
    Write-Host "Java 17 path example: C:\Program Files\Eclipse Adoptium\jdk-17.0.20.1-hotspot\bin" -ForegroundColor Yellow
    exit 1
}

# Delete existing truststore if it exists
if (Test-Path $TRUSTSTORE) {
    Remove-Item $TRUSTSTORE -Force
    Write-Host "Removed old truststore." -ForegroundColor Yellow
}

# Import CA cert into truststore
Write-Host ""
Write-Host "Importing Aiven CA certificate into truststore..." -ForegroundColor Cyan
& keytool -importcert `
    -alias $ALIAS `
    -file $CA_CERT `
    -keystore $TRUSTSTORE `
    -storepass $TRUSTSTORE_PASSWORD `
    -noprompt `
    -trustcacerts

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "SUCCESS: Truststore created at $TRUSTSTORE" -ForegroundColor Green
    Write-Host ""
    Write-Host "Truststore password: $TRUSTSTORE_PASSWORD" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "======================================================" -ForegroundColor Cyan
    Write-Host " NEXT STEP: Run the backend with:" -ForegroundColor Cyan
    Write-Host " cd C:\Users\bolla\Downloads\smart-bus-tracking\backend" -ForegroundColor White
    Write-Host " .\mvnw.cmd spring-boot:run" -ForegroundColor White
    Write-Host "======================================================" -ForegroundColor Cyan
} else {
    Write-Host "ERROR: Failed to create truststore. Check the error above." -ForegroundColor Red
    exit 1
}
