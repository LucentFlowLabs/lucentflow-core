#!/usr/bin/env pwsh

# LucentFlow Infrastructure Startup Script (Windows PowerShell)
# This script starts PostgreSQL, pgAdmin, and Metabase services

param(
    [switch]$Cleanup
)

# Error handling
$ErrorActionPreference = "Stop"

Write-Host "🚀 Starting LucentFlow Infrastructure..." -ForegroundColor Green

# Check if Docker is running
try {
    $null = docker version 2>$null
    Write-Host "✅ Docker is running" -ForegroundColor Green
} catch {
    Write-Host "❌ Docker is not installed or not running" -ForegroundColor Red
    Write-Host "Please install Docker Desktop for Windows" -ForegroundColor Yellow
    exit 1
}

# Check if Docker Compose V2 is available
try {
    $null = docker compose version 2>$null
    Write-Host "✅ Docker Compose V2 is available" -ForegroundColor Green
} catch {
    Write-Host "❌ Docker Compose V2 is not installed or not available" -ForegroundColor Red
    Write-Host "📋 Required: Docker Compose V2 (v2.20.0+)" -ForegroundColor Yellow
    Write-Host "💡 To upgrade:" -ForegroundColor Cyan
    Write-Host "   - Download Docker Desktop 4.25.0+ from https://www.docker.com/products/docker-desktop" -ForegroundColor White
    Write-Host "   - Or install Docker Compose V2 standalone: https://docs.docker.com/compose/install/" -ForegroundColor White
    exit 1
}

# Navigate to deployment directory
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$DeployDir = Join-Path $ScriptDir "lucentflow-deployment\docker"
Set-Location $DeployDir

# Check for .env file
$EnvFile = Join-Path $DeployDir ".env"
if (-not (Test-Path $EnvFile)) {
    Write-Host "🔧 .env file not found, copying from .env.example..." -ForegroundColor Yellow
    $EnvExample = Join-Path $DeployDir ".env.example"
    if (Test-Path $EnvExample) {
        Copy-Item $EnvExample $EnvFile
        Write-Host "✅ .env file created from template" -ForegroundColor Green
        Write-Host "💡 Please edit .env file with your specific configuration" -ForegroundColor Cyan
        Write-Host "🔑 Set your BASESCAN_API_KEY before running production" -ForegroundColor Yellow
    } else {
        Write-Host "❌ .env.example file not found!" -ForegroundColor Red
        exit 1
    }
}

# Load environment variables from .env file
$EnvVars = @{}
Get-Content $EnvFile | ForEach-Object {
    if ($_ -match '^\s*([^#].+?)\s*=\s*(.+?)\s*$') {
        $EnvVars[$matches[1]] = $matches[2]
    }
}

# Build Java command with optional proxy logic
$JavaProxyArgs = ""
if ($EnvVars['PROXY_HOST'] -and $EnvVars['PROXY_HOST'].Trim() -ne '""' -and $EnvVars['PROXY_HOST'].Trim() -ne '') {
    $PHost = $EnvVars['PROXY_HOST'].Trim('"')
    $PPort = if ($EnvVars['PROXY_PORT']) { $EnvVars['PROXY_PORT'].Trim('"') } else { "" }
    
    if ($PPort -and $PPort -ne '') {
        $JavaProxyArgs = "-Dhttps.proxyHost=$($PHost) -Dhttps.proxyPort=$($PPort)"
        Write-Host "🌐 Network Proxy detected: ${PHost}:${PPort}" -ForegroundColor Cyan
    } else {
        $JavaProxyArgs = "-Dhttps.proxyHost=$($PHost)"
        Write-Host "🌐 Network Proxy detected: ${PHost}" -ForegroundColor Cyan
    }
} else {
    Write-Host "🌐 No proxy configured. Direct connection enabled." -ForegroundColor Cyan
}

Write-Host "📦 Starting services with Docker Compose V2..." -ForegroundColor Blue

Write-Host "ℹ️  Note: First-time build might take a few minutes to download Maven dependencies inside Docker..." -ForegroundColor Cyan

# Start all services with Docker Compose V2
try {
    docker compose up -d
    Write-Host "✅ Docker Compose V2 started successfully" -ForegroundColor Green
} catch {
    Write-Host "❌ Failed to start Docker Compose V2: $_" -ForegroundColor Red
    exit 1
}

Write-Host "⏳ Waiting for services to be ready..." -ForegroundColor Yellow

# Wait for PostgreSQL to be ready
Write-Host "🔍 Checking PostgreSQL health..." -ForegroundColor Blue
$ready = $false
for ($i = 1; $i -le 30; $i++) {
    try {
        docker exec lucentflow-postgres pg_isready -U admin -d lucentflow 2>$null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✅ PostgreSQL is ready!" -ForegroundColor Green
            $ready = $true
            break
        }
    } catch {
        # Continue trying
    }
    Write-Host "⏳ Waiting for PostgreSQL... ($i/30)" -ForegroundColor Yellow
    Start-Sleep -Seconds 2
}

if (-not $ready) {
    Write-Host "❌ PostgreSQL failed to start within 60 seconds" -ForegroundColor Red
    Write-Host "🔍 Checking logs:" -ForegroundColor Yellow
    docker compose logs postgres
    if ($Cleanup) {
        Write-Host "🛑 Cleaning up services..." -ForegroundColor Yellow
        docker compose down
    }
    exit 1
}

# Wait for LucentFlow API to be healthy
Write-Host "🔍 Checking LucentFlow API health..." -ForegroundColor Blue
$apiReady = $false
for ($i = 1; $i -le 60; $i++) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing -TimeoutSec 2 2>$null
        if ($response.StatusCode -eq 200) {
            Write-Host "✅ LucentFlow API is healthy and ready!" -ForegroundColor Green
            $apiReady = $true
            break
        }
    } catch {
        # Continue trying
    }
    Write-Host "⏳ Waiting for LucentFlow API... ($i/60)" -ForegroundColor Yellow
    Start-Sleep -Seconds 2
}

if (-not $apiReady) {
    Write-Host "❌ LucentFlow API failed to become healthy within 120 seconds" -ForegroundColor Red
    Write-Host "🔍 Checking logs:" -ForegroundColor Yellow
    docker compose logs lucentflow-api
    if ($Cleanup) {
        Write-Host "🛑 Cleaning up services..." -ForegroundColor Yellow
        docker compose down
    }
    exit 1
}

# Wait a bit more for all services to stabilize
Start-Sleep -Seconds 5

Write-Host "🎯 Services started successfully!" -ForegroundColor Green
Write-Host ""
Write-Host "📊 Service URLs:" -ForegroundColor Cyan
Write-Host "  🗄️  PostgreSQL: localhost:5432" -ForegroundColor White
Write-Host "  📊  pgAdmin:   http://localhost:5050" -ForegroundColor White
Write-Host "  📈  Metabase:  http://localhost:3000" -ForegroundColor White
Write-Host ""
Write-Host "🚀 Starting LucentFlow application..." -ForegroundColor Green
Write-Host ""
Write-Host "💡 Navigate to lucentflow-api directory and run:" -ForegroundColor Cyan
Write-Host "   mvn spring-boot:run" -ForegroundColor White
Write-Host ""
Write-Host "📦 JAR Execution (Alternative):" -ForegroundColor Cyan
if ($JavaProxyArgs) {
    Write-Host "   java $JavaProxyArgs -jar lucentflow-api\target\lucentflow-api-1.0.0-RELEASE.jar" -ForegroundColor White
} else {
    Write-Host "   java -jar lucentflow-api\target\lucentflow-api-1.0.0-RELEASE.jar" -ForegroundColor White
}
Write-Host ""
Write-Host "�📖 API Documentation will be available at:" -ForegroundColor Cyan
Write-Host "   http://localhost:8080/swagger-ui.html" -ForegroundColor White
Write-Host ""
Write-Host "🔍 Health Check:" -ForegroundColor Cyan
Write-Host "   curl http://localhost:8080/actuator/health" -ForegroundColor White
Write-Host ""

# Navigate to API directory
Set-Location (Join-Path $DeployDir "..\..\lucentflow-api")

Write-Host "🎯 Ready to start LucentFlow application!" -ForegroundColor Green
Write-Host "Press Ctrl+C to stop all services" -ForegroundColor Yellow
Write-Host ""

# Setup cleanup handler
if ($Cleanup) {
    try {
        Write-Host "🛑 Monitoring services (cleanup mode enabled)..." -ForegroundColor Yellow
        
        # Keep script running and monitor for Ctrl+C
        while ($true) {
            Start-Sleep -Seconds 1
        }
    } finally {
        Write-Host "🛑 Stopping services..." -ForegroundColor Yellow
        Set-Location $DeployDir
        docker-compose down
        Write-Host "✅ Services stopped and cleaned up" -ForegroundColor Green
    }
} else {
    Write-Host "🎯 Infrastructure is ready! Run with -Cleanup flag to enable auto-cleanup." -ForegroundColor Green
    Write-Host "Example: .\start-infrastructure.ps1 -Cleanup" -ForegroundColor Cyan
}
