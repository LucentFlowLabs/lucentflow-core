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

echo "📦 Starting services with Docker Compose (strict env-file mode)..."

echo "ℹ️  Note: If this is your first run, Docker will download dependencies and compile the project. This may take a few minutes..."

# Start all services with explicit env-file to ignore shell variables
docker-compose --env-file .env up -d

echo "⏳ Waiting for services to be ready..."

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
    docker-compose logs postgres
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
    echo "   java $JAVA_PROXY_ARGS -jar lucentflow-api/target/lucentflow-api-1.0.0-RELEASE.jar"
else
    echo "   java -jar lucentflow-api/target/lucentflow-api-1.0.0-RELEASE.jar"
fi
echo ""
echo "�📖 API Documentation will be available at:"
echo "   http://localhost:8080/swagger-ui.html"
echo ""
echo "🔍 Health Check:"
echo "   curl http://localhost:8080/actuator/health"

# Navigate to API directory
cd "$API_DIR"

echo "🎯 Ready to start LucentFlow application!"
echo "Press Ctrl+C to stop all services"
echo ""

# Trap to handle cleanup
trap 'echo "🛑 Stopping services..."; docker-compose down; exit' INT

# Keep script running
tail -f /dev/null
