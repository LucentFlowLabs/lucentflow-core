# Local Development Setup

This guide explains how to run LucentFlow with a local H2 database without Docker.

## 🚀 Quick Start

### 1. Run with Local Profile
```bash
cd lucentflow-api
mvn spring-boot:run -Dspring.profiles.active=local
```

### 2. Access H2 Console
Open your browser and navigate to:
```
http://localhost:8080/h2-console
```

**JDBC URL:** `jdbc:h2:mem:lucentflow`
**Username:** `sa`
**Password:** (leave empty)

## 📋 Configuration Details

### Database Configuration
- **Type:** H2 In-Memory Database
- **Mode:** PostgreSQL Compatibility
- **DDL:** Create-Drop (fresh database each restart)
- **Console:** Enabled at `/h2-console`

### Application Configuration
- **Profile:** `local`
- **RPC URL:** `https://mainnet.base.org` (still connects to real blockchain)
- **Logging:** DEBUG level for SQL and application logs

## 🔧 Configuration Files

### `application-local.yml`
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:lucentflow;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
    username: sa
    password: 
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: create-drop
    show-sql: true
  h2:
    console:
      enabled: true
      path: /h2-console
```

## 🏛️ Architecture Validation

### BaseBlockPoller Configuration
The `BaseBlockPoller` automatically uses the local configuration because:

1. **Dependency Injection:** Uses `SyncStatusRepository` which connects to configured datasource
2. **No Hardcoded Settings:** All database connections use Spring's auto-configuration
3. **Profile-Based:** Spring profiles automatically select H2 when `local` profile is active

### Validation Component
The `LocalConfigurationValidator` component validates on startup:
- ✅ Local profile is active
- ✅ H2 database connection works
- ✅ RPC URL is configured
- ✅ H2 console is accessible

> **Note**: `LocalConfigurationValidator` is a Spring `@Component` that automatically
> validates the local development environment during application startup.

## 📊 Development Features

### Logging
- **SQL Queries:** Visible in console
- **Hibernate Statistics:** Enabled
- **Application Logs:** DEBUG level

### Database Management
- **Auto-Creation:** Tables created automatically
- **Sample Data:** No seed data (starts empty)
- **Persistence:** In-memory only (lost on restart)

## 🐛 Troubleshooting

### Common Issues

1. **Port 8080 in use:**
   ```bash
   # Change port in application-local.yml
   server:
     port: 8081
   ```

2. **Database connection issues:**
   - Check H2 console is accessible
   - Verify JDBC URL matches configuration

3. **Profile not active:**
   - Ensure `-Dspring.profiles.active=local` is included
   - Check startup logs for profile validation

### Validation Logs
Look for these messages on startup:
```
🔍 Validating Local Configuration...
✅ Local profile is active
✅ Database URL: jdbc:h2:mem:lucentflow
✅ H2 Database configured successfully
✅ RPC URL: https://mainnet.base.org
🚀 Local-Mock environment is ready!
```

## 🔄 Development Workflow

### Typical Development Session
1. Start application with local profile
2. Open H2 console to inspect data
3. Monitor logs for whale detections
4. Restart to get fresh database (create-drop)

## ⚠️ Development Considerations

### H2 Database Limitations

**Warning**: Complex SQL analytics should be verified against the Docker-based PostgreSQL environment because:

- **SQL Dialect Differences**: H2 PostgreSQL compatibility mode has limitations
- **Function Support**: Advanced PostgreSQL functions may not work in H2
- **Performance Characteristics**: Query optimization differs significantly
- **Data Types**: Some PostgreSQL-specific types have limited H2 support

**Recommended Workflow:**
1. Develop and test basic functionality with H2 (fast iteration)
2. Verify complex analytics with PostgreSQL before production deployment
3. Use PostgreSQL for performance testing and final validation

### Data Persistence
- **Development:** In-memory (lost on restart)
- **Testing:** Perfect for unit/integration tests
- **Production:** Use PostgreSQL configuration instead

## 🚦 Next Steps

### For Production
Switch to PostgreSQL configuration:
```bash
mvn spring-boot:run -Dspring.profiles.active=prod
```

### For Testing
Run integration tests with H2:
```bash
mvn test -Dspring.profiles.active=test
```

---

**Note:** This local setup uses the real Base blockchain RPC but stores data locally in H2 for development convenience.

*Documentation maintained by @author ArchLucent*
