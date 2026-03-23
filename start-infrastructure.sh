#!/bin/bash

# LucentFlow Infrastructure Startup Script
# This script starts PostgreSQL, pgAdmin, and Metabase services

set -e

# Get the absolute path of the directory where this script is located
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
PROJECT_ROOT="$SCRIPT_DIR"
DEPLOY_DIR="$PROJECT_ROOT/lucentflow-deployment/docker"
API_DIR="$PROJECT_ROOT/lucentflow-api"

echo "🚀 Starting LucentFlow Infrastructure..."
echo "📁 Project Root: $PROJECT_ROOT"
echo "📦 Deployment Dir: $DEPLOY_DIR"

# Check if Docker is running
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed or not running"
    exit 1
fi

# Check if Docker Compose V2 is available
if ! docker compose version &> /dev/null; then
    echo "❌ Docker Compose V2 is not installed or not available"
    echo "📋 Required: Docker Compose V2 (v2.20.0+)"
    echo "💡 To upgrade:"
    echo "   - Download Docker Desktop 4.25.0+ from https://www.docker.com/products/docker-desktop"
    echo "   - Or install Docker Compose V2 standalone: https://docs.docker.com/compose/install/"
    exit 1
fi

echo "✅ Docker and Docker Compose V2 are available"

# Navigate to deployment directory
cd "$DEPLOY_DIR"

# Check for .env file
if [ ! -f "$DEPLOY_DIR/.env" ]; then
    echo "🔧 .env file not found, copying from .env.example..."
    if [ -f "$DEPLOY_DIR/.env.example" ]; then
        cp "$DEPLOY_DIR/.env.example" "$DEPLOY_DIR/.env"
        echo "✅ .env file created from template"
        echo "💡 Please edit .env file with your specific configuration"
        echo "🔑 Set your BASESCAN_API_KEY before running production"
    else
        echo "❌ .env.example file not found!"
        exit 1
    fi
fi

# Load environment variables from .env file
if [ -f "$DEPLOY_DIR/.env" ]; then
    # Safe extraction of proxy variables
    ENV_FILE="$DEPLOY_DIR/.env"
    P_HOST=$(grep "^PROXY_HOST=" "$ENV_FILE" | cut -d'=' -f2- | tr -d '"')
    P_PORT=$(grep "^PROXY_PORT=" "$ENV_FILE" | cut -d'=' -f2- | tr -d '"')

    if [ -n "$P_HOST" ]; then
        echo "🌐 Network Proxy detected: $P_HOST:$P_PORT"
        export JAVA_PROXY_ARGS="-Dhttps.proxyHost=$P_HOST -Dhttps.proxyPort=$P_PORT"
    else
        echo "🌐 No proxy configured. Direct connection enabled."
        export JAVA_PROXY_ARGS=""
    fi
else
    echo "🌐 No proxy configured. Direct connection enabled."
    export JAVA_PROXY_ARGS=""
fi

echo "📦 Starting services with Docker Compose V2..."

echo "ℹ️  Note: If this is your first run, Docker will download dependencies and compile the project. This may take a few minutes..."

# Start all services with Docker Compose V2
docker compose up -d --build lucentflow-api

echo "⏳ Waiting for services to initialize..."
sleep 10

# Wait for PostgreSQL to be ready
echo "🔍 Checking PostgreSQL health..."
for i in {1..30}; do
    if docker exec lucentflow-postgres pg_isready -U admin -d lucentflow &> /dev/null; then
        echo "✅ PostgreSQL is ready!"
        break
    fi
    echo "⏳ Waiting for PostgreSQL... ($i/30)"
    sleep 2
done

if [ $i -eq 30 ]; then
    echo "❌ PostgreSQL failed to start within 60 seconds"
    echo "🔍 Checking logs:"
    docker compose logs postgres
    exit 1
fi

# Wait for LucentFlow API to be healthy
echo "🔍 Checking LucentFlow API health..."
for i in {1..60}; do
    if curl -f http://localhost:8080/actuator/health &> /dev/null; then
        echo "✅ LucentFlow API is healthy and ready!"
        break
    fi
    echo "⏳ Waiting for LucentFlow API... ($i/60)"
    sleep 2
done

if [ $i -eq 60 ]; then
    echo "❌ LucentFlow API failed to become healthy within 120 seconds"
    echo "🔍 Checking logs:"
    docker compose logs lucentflow-api
    exit 1
fi

# Wait a bit more for all services to stabilize
sleep 5

echo "🎯 Services started successfully!"
echo ""
echo "📊 Service URLs:"
echo "  🗄️  PostgreSQL: localhost:5432"
echo "  📊  pgAdmin:   http://localhost:5050"
echo "  📈  Metabase:  http://localhost:3000"
echo ""
echo "🚀 Starting LucentFlow application..."
echo ""
echo "💡 Navigate to lucentflow-api directory and run:"
echo "   mvn spring-boot:run"
echo ""
echo "📦 JAR Execution (Alternative):"
if [ -n "$JAVA_PROXY_ARGS" ]; then
    echo "   java $JAVA_PROXY_ARGS -jar target/lucentflow-api.jar"
else
    echo "   java -jar target/lucentflow-api.jar"
fi
echo ""
echo "�📖 API Documentation will be available at:"
echo "   http://localhost:8080/swagger-ui.html"
echo ""
echo "🔍 Health Check:"
echo "   curl http://localhost:8080/actuator/health"


echo "✅ Infrastructure is running in the background."
echo "💡 To stop all services later, run: docker compose -f $DEPLOY_DIR/docker-compose.yml down"
echo "----------------------------------------------------------------"


# Trap to handle cleanup
trap 'echo "🛑 Stopping services..."; docker compose down; exit' INT

# Navigate to API directory
cd "$API_DIR"
echo "📍 Current Directory: $(pwd)"
echo "🚀 You can now start the application."