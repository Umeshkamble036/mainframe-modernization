# CUSTACCT Spring Boot Application - Deployment Guide

## Overview

The CUSTACCT application is a Spring Boot 3.2.3 microservice that manages customer accounts and transactions. It supports **two deployment profiles**:

- **Development (`dev`)** - H2 in-memory database, auto-schema creation, verbose logging
- **Production (`prod`)** - PostgreSQL database, schema validation mode, minimal logging

---

## 1. Development Environment Setup (H2 Profile)

### Quick Start

The application defaults to the `dev` profile with H2 in-memory database. No external setup required.

```bash
# Navigate to project directory
cd custacct-spring

# Build the application
mvn clean package -DskipTests -Dmaven.compiler.fork=true \
  -Dmaven.compiler.executable="C:\Users\ADMIN\.jdk\jdk-17.0.16\bin\javac.exe"

# Run with development (H2) profile (default)
java -jar target/custacct-spring-1.0.0.jar

# Or explicitly specify the dev profile
java -jar target/custacct-spring-1.0.0.jar --spring.profiles.active=dev
```

### Access Points

Once running on `http://localhost:8080`:

| Endpoint | Purpose | Example |
|----------|---------|---------|
| GET `/api/customers/1` | Fetch customer by ID | Returns customer with 10 sample records |
| GET `/api/customers` | List all customers | Returns all customer records |
| POST `/api/batch/load-file` | Load transaction file | Upload .txt file for batch processing |
| GET `/h2-console` | H2 Database Console | Query database directly (dev only) |

### Sample Data

The `DataInitializer` class auto-loads 10 sample customers on startup:
- Customer IDs: 1001–1010
- Status: ACTIVE
- Opening balances: $1,000–$10,000

---

## 2. Production Environment Setup (PostgreSQL Profile)

### Prerequisites

1. **PostgreSQL installed** and running
   - Download: https://www.postgresql.org/download/
   - Confirm installation: `psql --version`

2. **Java 17** (already installed at `C:\Users\ADMIN\.jdk\jdk-17.0.16`)

3. **Maven** (already configured in project)

### Step 1: Create PostgreSQL Database and User

Connect to PostgreSQL and create the database and user:

```sql
-- Connect as superuser (default: postgres)
-- Windows: psql -U postgres

-- Create database
CREATE DATABASE custacct;

-- Create application user
CREATE USER custacct_user WITH PASSWORD 'your_secure_password';

-- Grant privileges
GRANT CONNECT ON DATABASE custacct TO custacct_user;
GRANT CREATE ON DATABASE custacct TO custacct_user;

\c custacct  -- Connect to custacct database

-- Grant all schema privileges
GRANT ALL PRIVILEGES ON SCHEMA public TO custacct_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO custacct_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO custacct_user;
```

### Step 2: Configure Environment Variables

**Option A: Environment Variables (Recommended)**

```bash
# Windows Command Prompt
set DB_USER=custacct_user
set DB_PASSWORD=your_secure_password

# Or use Windows System Environment Variables:
# 1. Right-click "This PC" > Properties > Advanced system settings
# 2. Environment Variables > New
# 3. Variable name: DB_USER, Value: custacct_user
# 4. Variable name: DB_PASSWORD, Value: your_secure_password
```

**Option B: Modify application-prod.properties**

Edit `src/main/resources/application-prod.properties`:

```properties
spring.datasource.username=custacct_user
spring.datasource.password=your_secure_password  # NOT recommended - hardcoding credentials
```

### Step 3: Build and Run with PostgreSQL Profile

```bash
# Build the application
mvn clean package -DskipTests -Dmaven.compiler.fork=true \
  -Dmaven.compiler.executable="C:\Users\ADMIN\.jdk\jdk-17.0.16\bin\javac.exe"

# Run with production (PostgreSQL) profile
java -jar target/custacct-spring-1.0.0.jar --spring.profiles.active=prod

# Or set environment variable first (Windows)
set DB_USER=custacct_user && set DB_PASSWORD=your_password && \
java -jar target/custacct-spring-1.0.0.jar --spring.profiles.active=prod
```

### Step 4: Verify PostgreSQL Connection

Once the application starts:

```bash
# Test API endpoint
curl http://localhost:8080/api/customers

# Or in PowerShell
Invoke-WebRequest http://localhost:8080/api/customers

# Verify in PostgreSQL
psql -U custacct_user -d custacct -c "SELECT COUNT(*) FROM customer_master;"
```

---

## 3. Database Schema

The application auto-creates tables on the **first run with a new database**. 

**Key Tables:**
- `customer_master` - Customer records
- `transactions` - Transaction records
- `batch_job_result` - Job execution history

### Manual Schema Creation (Optional)

If `spring.jpa.hibernate.ddl-auto=validate` mode fails, manually create tables:

```sql
-- In PostgreSQL (connected to custacct database)

CREATE TABLE customer_master (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(20),
    status CHAR(1),
    opening_balance DECIMAL(19,2),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE transactions (
    id BIGINT PRIMARY KEY,
    customer_id BIGINT REFERENCES customer_master(id),
    type VARCHAR(20),
    amount DECIMAL(19,2),
    reference VARCHAR(20),
    channel VARCHAR(20),
    created_at TIMESTAMP
);

CREATE TABLE batch_job_result (
    id BIGINT PRIMARY KEY,
    job_name VARCHAR(100),
    status VARCHAR(20),
    records_processed INT,
    records_failed INT,
    execution_time_ms LONG,
    created_at TIMESTAMP
);
```

---

## 4. Configuration Profile Details

### Development Profile (`application-dev.properties`)

```properties
# H2 In-Memory Database
spring.datasource.url=jdbc:h2:mem:custacct;DB_CLOSE_DELAY=-1
spring.datasource.driver-class-name=org.h2.Driver

# Auto-Create Schema
spring.jpa.hibernate.ddl-auto=create-drop

# H2 Web Console
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console

# Verbose Logging
logging.level.com.custacct=DEBUG
logging.level.org.hibernate.SQL=DEBUG
```

### Production Profile (`application-prod.properties`)

```properties
# PostgreSQL Database
spring.datasource.url=jdbc:postgresql://localhost:5432/custacct
spring.datasource.driver-class-name=org.postgresql.Driver
spring.datasource.username=${DB_USER:postgres}
spring.datasource.password=${DB_PASSWORD}

# Schema Validation (No Auto-Creation)
spring.jpa.hibernate.ddl-auto=validate

# Minimal Logging
logging.level.com.custacct=INFO
logging.level.org.springframework.batch=WARN
```

---

## 5. Switching Between Profiles

### From Dev (H2) to Prod (PostgreSQL)

```bash
# Stop current application (Ctrl+C)

# Set environment variables
set DB_USER=custacct_user
set DB_PASSWORD=your_password

# Run with prod profile
java -jar target/custacct-spring-1.0.0.jar --spring.profiles.active=prod
```

### From Prod (PostgreSQL) back to Dev (H2)

```bash
# Stop current application (Ctrl+C)

# Run with dev profile (default)
java -jar target/custacct-spring-1.0.0.jar
# Or explicitly:
java -jar target/custacct-spring-1.0.0.jar --spring.profiles.active=dev
```

---

## 6. Testing

### Unit Tests (All Profiles)

```bash
# Run tests with default dev profile
mvn test -Dmaven.compiler.fork=true \
  -Dmaven.compiler.executable="C:\Users\ADMIN\.jdk\jdk-17.0.16\bin\javac.exe"
```

### Integration Tests (PostgreSQL)

```bash
# Ensure PostgreSQL is running and configured
set DB_USER=custacct_user
set DB_PASSWORD=your_password

mvn verify -Dspring.profiles.active=prod -Dmaven.compiler.fork=true \
  -Dmaven.compiler.executable="C:\Users\ADMIN\.jdk\jdk-17.0.16\bin\javac.exe"
```

---

## 7. Troubleshooting

### PostgreSQL Connection Failed

**Error:** `Connection refused: connect`

**Solutions:**
1. Verify PostgreSQL is running: `psql --version`
2. Check credentials: `psql -U custacct_user -d custacct`
3. Verify in `application-prod.properties`:
   - URL: `jdbc:postgresql://localhost:5432/custacct`
   - Username: `custacct_user`
   - Password: Set correctly in environment variable or properties file

### Table Validation Errors

**Error:** `Relation "customer_master" not found`

**Solutions:**
1. Temporarily set `spring.jpa.hibernate.ddl-auto=create` to auto-create tables
2. Or manually run the SQL schema creation script (see Section 3)
3. Then change back to `validate` mode for production

### H2 Console Not Loading

**Error:** `Cannot GET /h2-console`

**Solution:** H2 console only available in **dev profile**. Check you're running:
```bash
java -jar target/custacct-spring-1.0.0.jar --spring.profiles.active=dev
```

---

## 8. Quick Reference

| Task | Command |
|------|---------|
| Build project | `mvn clean package -DskipTests` |
| Run (H2/Dev) | `java -jar target/custacct-spring-1.0.0.jar` |
| Run (PostgreSQL/Prod) | `java -jar target/custacct-spring-1.0.0.jar --spring.profiles.active=prod` |
| Run tests | `mvn test` |
| Access API (dev) | `curl http://localhost:8080/api/customers` |
| Access H2 console | `http://localhost:8080/h2-console` |
| List customers (psql) | `psql -U custacct_user -d custacct -c "SELECT * FROM customer_master;"` |

---

## 9. Additional Resources

- **Spring Boot Profiles**: https://spring.io/blog/2015/04/12/managing-configuration-with-spring-boot
- **PostgreSQL Documentation**: https://www.postgresql.org/docs/
- **Spring Data JPA**: https://spring.io/projects/spring-data-jpa
- **Spring Batch**: https://spring.io/projects/spring-batch

---

**Last Updated:** 2024  
**Application Version:** 1.0.0  
**Spring Boot Version:** 3.2.3
