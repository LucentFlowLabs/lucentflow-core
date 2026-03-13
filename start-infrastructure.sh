#!/bin/bash

# LucentFlow Infrastructure Startup Script
# This script starts PostgreSQL, pgAdmin, and Metabase services

set -e

echo "🚀 Starting LucentFlow Infrastructure..."

# Check if Docker is running
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed or not running"
    exit 1
fi

# Navigate to deployment directory
cd "$(dirname "$0")/lucentflow-deployment"

echo "📦 Starting services with Docker Compose..."

# Start all services
docker-compose up -d

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
echo "📖 API Documentation will be available at:"
echo "   http://localhost:8080/swagger-ui.html"
echo ""
echo "🔍 Health Check:"
echo "   curl http://localhost:8080/actuator/health"

# Navigate to API directory
cd ../lucentflow-api

echo "🎯 Ready to start LucentFlow application!"
echo "Press Ctrl+C to stop all services"
echo ""

# Trap to handle cleanup
trap 'echo "🛑 Stopping services..."; docker-compose down; exit' INT

# Keep script running
tail -f /dev/null
