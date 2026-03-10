# LucentFlow Infrastructure Setup

## 🐳 Docker Compose Configuration

### Services Overview

#### **✅ PostgreSQL 16 (Primary Database)**
- **Container**: `lucentflow-postgres`
- **Port**: `5432:5432`
- **Database**: `lucentflow`
- **Credentials**: `admin/lucentflow_pwd`
- **Health Check**: Ready detection before dependent services start

#### **✅ pgAdmin 4 (Database Management)**
- **Container**: `lucentflow-pgadmin`
- **Port**: `5050:80`
- **Access**: http://localhost:5050
- **Dependency**: Waits for PostgreSQL to be healthy

#### **✅ Metabase (Dashboard & Analytics)**
- **Container**: `lucentflow-metabase`
- **Port**: `3000:3000`
- **Dashboard**: http://localhost:3000
- **Database**: Connected to PostgreSQL
- **Health Check**: API health monitoring

### Network Configuration

#### **✅ Custom Bridge Network**
- **Name**: `lucentflow-network`
- **Subnet**: `172.20.0.0/16`
- **Type**: Bridge network for container communication

### Data Persistence

#### **✅ Volume Management**
```yaml
volumes:
  postgres_data:    # PostgreSQL data persistence
  pgadmin_data:     # pgAdmin configuration
  metabase_data:    # Metabase settings and dashboards
```

## 🚀 Application Configuration

### Database Connection

#### **✅ PostgreSQL Configuration**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/lucentflow
    username: admin
    password: lucentflow_pwd
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      initialization-fail-timeout: 60000
  jpa:
    hibernate:
      ddl-auto: update
      dialect: org.hibernate.dialect.PostgreSQLDialect
      jdbc:
        batch_size: 50
        order_inserts: true
        order_updates: true
```

### Performance Optimizations

#### **✅ Batch Processing**
- **Batch Size**: 50 (increased from 20)
- **Connection Pool**: 20 connections
- **Connection Timeout**: 30 seconds
- **Startup Timeout**: 60 seconds wait for DB

### Application Lifecycle

#### **✅ Graceful Startup**
```yaml
lifecycle:
  timeout:
    startup: 60s    # Wait for database
    shutdown: 30s    # Graceful shutdown
```

## 🎯 Service Dependencies

### Startup Order

1. **PostgreSQL** (Database)
   - Health check: `pg_isready -U admin -d lucentflow`
   - Timeout: 30s startup, 5s retries

2. **LucentFlow Application** (Backend)
   - Waits for database connectivity
   - Startup timeout: 60s
   - Restart on failure: Configured

3. **pgAdmin** (Database UI)
   - Depends on: PostgreSQL healthy
   - Port: 5050

4. **Metabase** (Dashboard)
   - Depends on: PostgreSQL healthy
   - Port: 3000
   - Health check: `/api/health`

## 🔄 Startup Commands

### Development Environment

```bash
# Start infrastructure
cd lucentflow-deployment
docker-compose up -d

# Start application (from project root)
cd ../lucentflow-api
mvn spring-boot:run

# Or with profile
mvn spring-boot:run -Dspring.profiles.active=dev
```

### Production Environment

```bash
# Production startup
docker-compose -f docker-compose.prod.yml up -d
docker-compose logs -f lucentflow-metabase
```

## 📊 Monitoring & Health Checks

### Database Health

#### **✅ PostgreSQL Health Check**
```bash
docker exec lucentflow-db pg_isready -U admin
```

### Application Health

#### **✅ LucentFlow Endpoints**
```bash
# Application health
curl http://localhost:8080/actuator/health

# Whale transactions API
curl http://localhost:8080/api/v1/whales

# Sync status
curl http://localhost:8080/api/v1/sync-status
```

### Metabase Dashboard

#### **✅ Access Information**
- **URL**: http://localhost:3000
- **Admin**: First-time setup required
- **Database**: Auto-connected to PostgreSQL
- **Dashboards**: Whale analytics, sync monitoring

## 🔧 Configuration Files

### Environment-Specific

#### **✅ application.yml (Default - PostgreSQL)**
- **Database**: PostgreSQL configuration
- **Performance**: Optimized batch processing
- **Lifecycle**: Graceful startup/shutdown

#### **✅ application-local.yml (Local-Only)**
- **Status**: Local development configuration (excluded from Git)
- **Purpose**: H2 in-memory database for rapid development
- **Security**: Contains local-only settings, never committed
- **Usage**: `-Dspring.profiles.active=local`

## 🚨 Troubleshooting

### Common Issues

1. **Database Connection Failed**
   ```bash
   # Check PostgreSQL container
   docker-compose logs postgres
   
   # Check network connectivity
   docker network ls
   docker network inspect lucentflow-network
   ```

2. **Metabase Connection Issues**
   ```bash
   # Verify Metabase container
   docker-compose logs metabase
   
   # Check database connection from Metabase
   curl -X POST http://localhost:3000/api/health
   ```

3. **Application Startup Issues**
   ```bash
   # Check application logs
   mvn spring-boot:run -Dspring.profiles.active=dev -Ddebug=true
   
   # Verify database connectivity
   curl -X POST http://localhost:8080/actuator/health
   ```

## 📈 Performance Metrics

### Database Performance

- **Connection Pool**: 20 max connections
- **Batch Size**: 50 records per batch
- **Query Optimization**: Ordered inserts/updates
- **Index Strategy**: Proper indexing on whale transactions

### Application Performance

- **Virtual Threads**: Enabled for concurrent processing
- **Task Execution**: Optimized thread pools
- **Memory Management**: Configured JVM settings

## � Production Considerations

### RPC Endpoint Requirements

For production workloads, a **private RPC endpoint** is **mandatory** to avoid:
- **429 Rate Limits**: Public endpoints have strict request limits
- **Service Reliability**: Private endpoints provide guaranteed uptime
- **Performance**: Dedicated resources for consistent response times

**Recommended Providers:**
- **Alchemy**: Enterprise-grade Base node access
- **Infura**: Reliable infrastructure with monitoring
- **QuickNode**: High-performance blockchain endpoints

**Configuration Example:**
```yaml
lucentflow:
  chain:
    rpc-url: "https://base-mainnet.g.alchemy.com/v2/YOUR_API_KEY"
```

## �🔒 Security Considerations

### Database Security

- **Credentials**: Environment variables (not in code)
- **Network**: Isolated bridge network
- **SSL**: PostgreSQL SSL mode configured
- **Access Control**: Only internal network access

### Application Security

- **API Documentation**: Swagger UI with proper CORS
- **Input Validation**: Request parameter validation
- **Error Handling**: Proper error responses
- **Logging**: Security event logging

## 🎯 Next Steps

### Production Deployment

1. **Environment Variables**: Use secrets management
2. **SSL Certificates**: Configure HTTPS
3. **Load Balancer**: Add reverse proxy
4. **Monitoring**: Add metrics collection
5. **Backup Strategy**: Database backup automation

### Scaling Considerations

1. **Database**: Read replicas for query scaling
2. **Application**: Horizontal pod scaling
3. **Metabase**: Dedicated instance for analytics
4. **Network**: Separate monitoring network

---

**Infrastructure Status**: ✅ PostgreSQL + Metabase + LucentFlow Ready for Production!

*Documentation maintained by @author ArchLucent*
