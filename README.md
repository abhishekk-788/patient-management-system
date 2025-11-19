# 🏥 Patient Management System

A comprehensive microservices-based patient management system built with Spring Boot, featuring authentication, patient management, billing, and analytics capabilities.

## 📋 Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [System Design](#system-design)
- [Services](#services)
- [Technology Stack](#technology-stack)
- [Data Flow](#data-flow)
- [Communication Patterns](#communication-patterns)
- [Infrastructure](#infrastructure)
- [Getting Started](#getting-started)
- [API Documentation](#api-documentation)
- [Testing](#testing)

## 🎯 Overview

This project implements a distributed microservices architecture for managing patient data, authentication, billing, and analytics. The system follows modern software engineering principles including:

- **Microservices Architecture**: Independent, scalable services
- **API Gateway Pattern**: Single entry point for all client requests
- **Event-Driven Architecture**: Asynchronous communication via Kafka
- **gRPC Communication**: High-performance inter-service communication
- **JWT Authentication**: Secure token-based authentication
- **Infrastructure as Code**: AWS CDK for infrastructure provisioning

## 🏗️ Architecture

### High-Level Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         Client Applications                      │
│                    (Web, Mobile, External APIs)                  │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             │ HTTP/REST
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                         API Gateway                              │
│                    (Spring Cloud Gateway)                        │
│  - Route Management                                              │
│  - JWT Validation Filter                                         │
│  - Load Balancing                                                │
└────────────┬───────────────────────┬───────────────────────────┘
             │                        │
             │ /auth/**              │ /api/patients/**
             │                        │ (JWT Protected)
             ▼                        ▼
┌──────────────────────┐   ┌──────────────────────────────────────┐
│   Auth Service       │   │      Patient Service                 │
│  (Port: 4005)        │   │      (Port: 4000)                    │
│                      │   │                                      │
│  - User Management   │   │  - Patient CRUD Operations           │
│  - JWT Generation    │   │  - Data Validation                   │
│  - Token Validation  │   │  - Event Publishing                  │
└──────────┬───────────┘   └──────┬───────────────┬──────────────┘
           │                      │               │
           │                      │               │
           │                      │ gRPC          │ Kafka
           │                      │               │
           │                      ▼               ▼
           │            ┌──────────────────┐  ┌──────────────────┐
           │            │  Billing Service  │  │ Analytics Service│
           │            │  (Port: 4002)     │  │  (Port: 4003)   │
           │            │  (gRPC: 9002)     │  │                  │
           │            │                   │  │  - Event Consumer│
           │            │  - Account Mgmt   │  │  - Analytics     │
           │            └───────────────────┘  └──────────────────┘
           │
           ▼
┌──────────────────────┐
│   PostgreSQL DB      │
│   (Auth Service DB)  │
└──────────────────────┘

┌──────────────────────┐
│   PostgreSQL DB      │
│  (Patient Service DB)│
└──────────────────────┘

┌──────────────────────┐
│   Kafka Cluster      │
│   (Event Streaming)  │
└──────────────────────┘
```

### Architecture Layers

Each microservice follows a layered architecture:

```
┌─────────────────────────────────────────┐
│         Controller Layer                │
│  - REST API Endpoints                   │
│  - Request Validation                   │
│  - Response Mapping                     │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│         Service Layer                   │
│  - Business Logic                      │
│  - DTO ↔ Entity Mapping                │
│  - External Service Calls              │
│  - Event Publishing                    │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│       Repository Layer                  │
│  - Data Access (JPA/Hibernate)         │
│  - Database Queries                    │
│  - Transaction Management               │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│         Database Layer                  │
│  - PostgreSQL                           │
│  - Data Persistence                     │
└─────────────────────────────────────────┘
```

## 🎨 System Design

### Design Principles

1. **Separation of Concerns**: Each service has a single, well-defined responsibility
2. **Loose Coupling**: Services communicate via well-defined interfaces (REST, gRPC, Events)
3. **High Cohesion**: Related functionality is grouped within the same service
4. **Scalability**: Services can be scaled independently based on load
5. **Resilience**: Services handle failures gracefully with proper error handling

### Design Patterns Used

#### 1. **API Gateway Pattern**
- **Location**: `api-gateway` service
- **Purpose**: Single entry point for all client requests
- **Features**:
  - Request routing to appropriate services
  - JWT token validation
  - Load balancing
  - API documentation aggregation

#### 2. **Service Discovery Pattern**
- **Implementation**: AWS Cloud Map (via CDK)
- **Purpose**: Dynamic service location and communication

#### 3. **Event-Driven Architecture**
- **Implementation**: Apache Kafka
- **Purpose**: Asynchronous communication between services
- **Use Case**: Patient creation events trigger analytics processing

#### 4. **Circuit Breaker Pattern** (Implicit)
- **Implementation**: Spring Cloud Gateway resilience
- **Purpose**: Prevent cascading failures

#### 5. **Repository Pattern**
- **Implementation**: Spring Data JPA
- **Purpose**: Abstraction of data access layer

#### 6. **DTO Pattern**
- **Purpose**: Separate internal entities from external API contracts
- **Benefits**: 
  - API versioning flexibility
  - Data validation at boundaries
  - Security (hiding internal structure)

## 🔧 Services

### 1. API Gateway Service

**Port**: `4004`

**Responsibilities**:
- Route incoming requests to appropriate microservices
- Validate JWT tokens for protected endpoints
- Aggregate API documentation
- Load balancing

**Key Components**:
- `JwtValidationGatewayFilterFactory`: Custom filter for JWT validation
- Route configuration for auth and patient services

**Routes**:
- `/auth/**` → Auth Service (No authentication required)
- `/api/patients/**` → Patient Service (JWT validation required)
- `/api-docs/**` → Service API documentation

### 2. Auth Service

**Port**: `4005`

**Responsibilities**:
- User authentication
- JWT token generation and validation
- User management

**Key Components**:
- `AuthController`: REST endpoints for login and token validation
- `AuthService`: Business logic for authentication
- `JwtUtil`: JWT token generation and validation utilities
- `UserService`: User management operations
- `SecurityConfig`: Spring Security configuration

**Endpoints**:
- `POST /login`: Authenticate user and generate JWT token
- `GET /validate`: Validate JWT token

**Database**: PostgreSQL (Auth Service DB)

### 3. Patient Service

**Port**: `4000`

**Responsibilities**:
- Patient CRUD operations
- Patient data validation
- Integration with Billing Service (gRPC)
- Event publishing to Kafka

**Key Components**:
- `PatientController`: REST API endpoints
- `PatientService`: Business logic and orchestration
- `PatientRepository`: Data access layer
- `BillingServiceGrpcClient`: gRPC client for billing service
- `KafkaProducer`: Event publisher for patient events
- `PatientMapper`: DTO ↔ Entity mapping

**Endpoints**:
- `GET /patients`: Get all patients
- `POST /patients`: Create new patient
- `PUT /patients/{id}`: Update patient
- `DELETE /patients/{id}`: Delete patient

**Database**: PostgreSQL (Patient Service DB)

**External Integrations**:
- **gRPC**: Calls Billing Service to create billing account
- **Kafka**: Publishes patient creation events

### 4. Billing Service

**Ports**: `4002` (HTTP), `9002` (gRPC)`

**Responsibilities**:
- Billing account management
- gRPC service implementation

**Key Components**:
- `BillingGrpcService`: gRPC service implementation
- Protocol Buffers for service contract

**gRPC Methods**:
- `CreateBillingAccount`: Creates a billing account for a patient

**Communication**: gRPC (Protocol Buffers)

### 5. Analytics Service

**Port**: `4003`

**Responsibilities**:
- Consume patient events from Kafka
- Perform analytics and reporting
- Event processing

**Key Components**:
- `KafkaConsumer`: Kafka event consumer
- Event deserialization using Protocol Buffers

**Kafka Topics**:
- `patient`: Patient-related events

## 🛠️ Technology Stack

### Backend Framework
- **Spring Boot 3.5.7**: Main framework
- **Java 21**: Programming language
- **Spring Cloud Gateway**: API Gateway implementation
- **Spring Data JPA**: Data persistence
- **Spring Security**: Authentication and authorization
- **Spring Kafka**: Kafka integration

### Communication Protocols
- **REST API**: HTTP/JSON for external communication
- **gRPC**: High-performance inter-service communication
- **Protocol Buffers**: Data serialization for gRPC and Kafka

### Message Broker
- **Apache Kafka**: Event streaming and asynchronous communication

### Database
- **PostgreSQL**: Relational database for persistent storage

### Authentication
- **JWT (JSON Web Tokens)**: Token-based authentication
- **JJWT Library**: JWT implementation

### Infrastructure
- **AWS CDK (Java)**: Infrastructure as Code
- **Docker**: Containerization
- **AWS ECS Fargate**: Container orchestration
- **AWS RDS**: Managed PostgreSQL databases
- **AWS MSK**: Managed Kafka service
- **LocalStack**: Local AWS services for development

### Development Tools
- **Maven**: Build and dependency management
- **Lombok**: Code generation
- **SpringDoc OpenAPI**: API documentation
- **REST Assured**: Integration testing

## 🔄 Data Flow

### 1. User Authentication Flow

```
Client
  │
  │ POST /auth/login
  │ { email, password }
  ▼
API Gateway
  │
  │ Route to /auth/**
  ▼
Auth Service
  │
  │ 1. Validate credentials
  │ 2. Generate JWT token
  │
  ▼
Client
  │
  │ Receives JWT token
```

### 2. Create Patient Flow

```
Client
  │
  │ POST /api/patients
  │ Authorization: Bearer <token>
  │ { name, email, address, dateOfBirth, registrationDate }
  ▼
API Gateway
  │
  │ 1. Extract JWT token
  │ 2. Validate token with Auth Service
  │ 3. Route to Patient Service
  ▼
Patient Service
  │
  │ 1. Validate request DTO
  │ 2. Check email uniqueness
  │ 3. Convert DTO → Entity
  │ 4. Save to Database
  │
  ├─► Billing Service (gRPC)
  │   │ Create billing account
  │   │
  │   └─► Returns billing account ID
  │
  └─► Kafka Producer
      │ Publish PatientEvent
      │
      ▼
  Kafka Topic: "patient"
      │
      ▼
  Analytics Service
      │
      │ 1. Consume event
      │ 2. Deserialize Protocol Buffer
      │ 3. Process analytics
      │
      ▼
  Analytics Processing Complete
```

### 3. Get Patients Flow

```
Client
  │
  │ GET /api/patients
  │ Authorization: Bearer <token>
  ▼
API Gateway
  │
  │ 1. Validate JWT token
  │ 2. Route to Patient Service
  ▼
Patient Service
  │
  │ 1. Query database
  │ 2. Convert Entity → DTO
  │ 3. Return list of patients
  ▼
Client
  │
  │ Receives patient list
```

## 📡 Communication Patterns

### 1. Synchronous Communication

#### REST API (HTTP/JSON)
- **Used for**: Client-to-service communication
- **Services**: All services expose REST endpoints
- **Protocol**: HTTP/1.1 with JSON payloads

#### gRPC (Protocol Buffers)
- **Used for**: Inter-service communication (Patient → Billing)
- **Benefits**: 
  - High performance
  - Type-safe contracts
  - Streaming support
- **Protocol**: HTTP/2 with Protocol Buffer serialization

### 2. Asynchronous Communication

#### Event-Driven (Kafka)
- **Used for**: Decoupled service communication
- **Pattern**: Publish-Subscribe
- **Use Case**: Patient Service publishes events, Analytics Service consumes them
- **Benefits**:
  - Loose coupling
  - Scalability
  - Event replay capability

### 3. Service-to-Service Communication Matrix

| Source Service | Target Service | Protocol | Purpose |
|---------------|----------------|----------|---------|
| Client | API Gateway | HTTP/REST | All client requests |
| API Gateway | Auth Service | HTTP/REST | Token validation |
| API Gateway | Patient Service | HTTP/REST | Patient operations |
| Patient Service | Billing Service | gRPC | Create billing account |
| Patient Service | Kafka | Kafka | Publish patient events |
| Kafka | Analytics Service | Kafka | Consume patient events |

## 🏗️ Infrastructure

### Infrastructure as Code (AWS CDK)

The project uses AWS CDK to define infrastructure:

**Components**:
- **VPC**: Virtual Private Cloud with 2 Availability Zones
- **ECS Cluster**: Container orchestration
- **RDS Instances**: PostgreSQL databases for Auth and Patient services
- **MSK Cluster**: Managed Kafka cluster
- **Fargate Services**: Serverless container execution
- **Application Load Balancer**: For API Gateway service
- **Route 53 Health Checks**: Database health monitoring
- **Cloud Map**: Service discovery

**Key Features**:
- Bootstrapless synthesizer for LocalStack compatibility
- Environment-specific configuration
- Dependency management between services
- Logging and monitoring setup

### Containerization

Each service includes a `Dockerfile` for containerization:
- Multi-stage builds (where applicable)
- Optimized image sizes
- Health check configurations

## 🚀 Getting Started

### Prerequisites

- Java 21
- Maven 3.8+
- Docker and Docker Compose
- PostgreSQL (or use Docker)
- Kafka (or use Docker)
- LocalStack (for local AWS services)

### Local Development Setup

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd project-management
   ```

2. **Start Infrastructure Services**
   ```bash
   # Start PostgreSQL, Kafka, etc. using Docker Compose
   # (Create docker-compose.yml if not present)
   docker-compose up -d
   ```

3. **Configure Services**

   **Auth Service** (`auth-service/src/main/resources/application.properties`):
   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/auth-service-db
   spring.datasource.username=postgres
   spring.datasource.password=password
   ```

   **Patient Service** (`patient-service/src/main/resources/application.properties`):
   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/patient-service-db
   spring.kafka.bootstrap-servers=localhost:9092
   billing.service.address=localhost
   billing.service.grpc.port=9002
   ```

4. **Build Services**
   ```bash
   # Build all services
   mvn clean install
   ```

5. **Run Services** (in separate terminals)
   ```bash
   # Terminal 1: Auth Service
   cd auth-service && mvn spring-boot:run

   # Terminal 2: Patient Service
   cd patient-service && mvn spring-boot:run

   # Terminal 3: Billing Service
   cd billing-service && mvn spring-boot:run

   # Terminal 4: Analytics Service
   cd analytics-service && mvn spring-boot:run

   # Terminal 5: API Gateway
   cd api-gateway && mvn spring-boot:run
   ```

### Docker Deployment

1. **Build Docker Images**
   ```bash
   docker build -t auth-service ./auth-service
   docker build -t patient-service ./patient-service
   docker build -t billing-service ./billing-service
   docker build -t analytics-service ./analytics-service
   docker build -t api-gateway ./api-gateway
   ```

2. **Deploy with Docker Compose**
   ```bash
   docker-compose up -d
   ```

### AWS Deployment (via CDK)

1. **Synthesize CDK Stack**
   ```bash
   cd infrastructure
   mvn compile
   mvn exec:java -Dexec.mainClass="com.pm.Main"
   ```

2. **Deploy to AWS** (or LocalStack)
   ```bash
   # For LocalStack
   ./localstack-deploy.sh
   ```

## 📚 API Documentation

### Authentication

#### Login
```http
POST /auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password123"
}
```

**Response**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

#### Validate Token
```http
GET /validate
Authorization: Bearer <token>
```

**Response**: `true` or `false`

### Patient Management

#### Get All Patients
```http
GET /api/patients
Authorization: Bearer <token>
```

**Response**:
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "John Doe",
    "email": "john@example.com",
    "address": "123 Main St",
    "dateOfBirth": "1990-01-01",
    "registrationDate": "2024-01-01"
  }
]
```

#### Create Patient
```http
POST /api/patients
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "John Doe",
  "email": "john@example.com",
  "address": "123 Main St",
  "dateOfBirth": "1990-01-01",
  "registrationDate": "2024-01-01"
}
```

#### Update Patient
```http
PUT /api/patients/{id}
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "John Updated",
  "email": "john.updated@example.com",
  "address": "456 New St"
}
```

#### Delete Patient
```http
DELETE /api/patients/{id}
Authorization: Bearer <token>
```

### API Documentation (Swagger)

- **Auth Service**: `http://localhost:4004/api-docs/auth`
- **Patient Service**: `http://localhost:4004/api-docs/patients`

## 🧪 Testing

### Integration Tests

The project includes integration tests using REST Assured:

```bash
cd integration-tests
mvn test
```

**Test Files**:
- `AuthIntegrationTest.java`: Tests authentication flow
- `PatientIntegrationTest.java`: Tests patient CRUD operations with authentication

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=PatientIntegrationTest
```

## 📁 Project Structure

```
project-management/
├── api-gateway/              # API Gateway service
│   ├── src/main/java/
│   │   └── com/pm/apigateway/
│   │       ├── filter/       # JWT validation filter
│   │       └── exception/    # Exception handlers
│   └── src/main/resources/
│       └── application.yml   # Gateway routing config
│
├── auth-service/             # Authentication service
│   ├── src/main/java/
│   │   └── com/pm/authservice/
│   │       ├── controller/   # REST controllers
│   │       ├── service/       # Business logic
│   │       ├── config/        # Security configuration
│   │       └── util/          # JWT utilities
│   └── src/main/resources/
│       └── data.sql          # Initial data
│
├── patient-service/          # Patient management service
│   ├── src/main/java/
│   │   └── com/pm/patientservice/
│   │       ├── controller/   # REST controllers
│   │       ├── service/      # Business logic
│   │       ├── repository/   # Data access
│   │       ├── model/        # Entity models
│   │       ├── dto/          # Data transfer objects
│   │       ├── mapper/       # DTO mappers
│   │       ├── grpc/         # gRPC client
│   │       ├── kafka/        # Kafka producer
│   │       └── exception/    # Exception handlers
│   ├── src/main/proto/      # Protocol Buffer definitions
│   └── src/main/resources/
│
├── billing-service/          # Billing service (gRPC)
│   ├── src/main/java/
│   │   └── com/pm/billingservice/
│   │       └── grpc/         # gRPC service implementation
│   └── src/main/proto/      # Protocol Buffer definitions
│
├── analytics-service/        # Analytics service
│   ├── src/main/java/
│   │   └── com/pm/analyticsservice/
│   │       └── kafka/        # Kafka consumer
│   └── src/main/proto/      # Protocol Buffer definitions
│
├── infrastructure/          # AWS CDK infrastructure code
│   └── src/main/java/
│       └── com/pm/stack/
│           └── LocalStack.java  # CDK stack definition
│
├── integration-tests/       # Integration test suite
│   └── src/test/java/
│
├── api-requests/            # HTTP request examples
│   ├── auth-service/
│   └── patient-service/
│
└── grpc-requests/           # gRPC request examples
    └── billing-service/
```

## 🔐 Security

### Authentication Flow

1. **Login**: User authenticates with email/password
2. **Token Generation**: Auth Service generates JWT token
3. **Token Validation**: API Gateway validates token for protected routes
4. **Request Processing**: Validated requests are forwarded to services

### Security Features

- **JWT-based Authentication**: Stateless token authentication
- **Password Encryption**: BCrypt password hashing
- **Role-based Access**: JWT tokens include user roles
- **API Gateway Protection**: Centralized authentication at gateway level

## 📊 Monitoring and Logging

- **CloudWatch Logs**: Centralized logging via AWS CloudWatch
- **Application Logging**: SLF4J with Logback
- **Health Checks**: Route 53 health checks for databases
- **Service Logs**: Each service logs to `/ecs/{service-name}` log groups

## 🚧 Future Enhancements

- [ ] Add more comprehensive error handling
- [ ] Implement distributed tracing (Zipkin/Jaeger)
- [ ] Add metrics collection (Prometheus)
- [ ] Implement caching layer (Redis)
- [ ] Add database migration tool (Flyway/Liquibase)
- [ ] Enhance security with OAuth2
- [ ] Add comprehensive unit tests
- [ ] Implement API rate limiting
- [ ] Add service mesh (Istio) for advanced traffic management

---

**Built with ❤️ using Spring Boot and Microservices Architecture**
