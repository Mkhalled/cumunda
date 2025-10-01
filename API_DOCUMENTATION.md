# API Documentation - Form Submission Workflow

## Overview

This document provides API documentation for the Form Submission Workflow Orchestrator, which handles insurance quote and contract processes.

---

## 📡 REST API Endpoints

### Base URL
```
http://localhost:8080/api/workflow
```

---

## 🚀 Workflow APIs

### 1. Start Form Submission Process

**Endpoint**: `POST /form-submission/start`

**Description**: Initiates a new form submission workflow for insurance quotes.

**Request Body**:
```json
{
  "customerName": "John Doe",
  "email": "john.doe@example.com",
  "formData": {
    "personalInfo": {
      "age": 35,
      "profession": "Software Engineer"
    },
    "insuranceDetails": {
      "coverageType": "LIFE_INSURANCE",
      "coverageAmount": 500000
    }
  },
  "requestedCoverage": "FULL",
  "premium": 150.0,
  "documents": ["document1.pdf", "document2.pdf"]
}
```

**Response**:
```json
{
  "processInstanceId": "proc-123-456",
  "customerId": "customer-uuid",
  "status": "STARTED",
  "message": "Form submission process started successfully"
}
```

---

### 2. Get Process Status

**Endpoint**: `GET /process/{processInstanceId}/status`

**Description**: Retrieves the current status of a workflow process.

**Response**:
```json
{
  "processInstanceId": "proc-123-456",
  "status": "RUNNING",
  "activeActivities": ["check-tariff-conditions"],
  "variables": {
    "customerId": "customer-uuid",
    "customerName": "John Doe",
    "tariffDecision": "SPECIFIC"
  }
}
```

---

### 3. Complete Quote Modification

**Endpoint**: `POST /process/{processInstanceId}/complete-quote-modification`

**Description**: Completes the quote modification step in the workflow.

**Request Body**:
```json
{
  "modifiedPremium": 200.0,
  "acceptedQuote": true,
  "modificationNotes": "Customer requested higher coverage"
}
```

**Response**:
```json
{
  "processInstanceId": "proc-123-456",
  "status": "QUOTE_MODIFIED",
  "message": "Quote modification completed successfully"
}
```

---

## 🔌 External API Integrations

The workflow integrates with the following external systems:

### 1. Simulator API
- **Purpose**: Determine tariff conditions (SPECIFIC/STANDARD)
- **Endpoint**: Configured via `simulator.api.url`
- **Authentication**: API Key via `simulator.api.key`

### 2. Profitability Simulator
- **Purpose**: Analyze contract profitability
- **Threshold**: Configurable via `profitability.threshold`

### 3. E-Sign Platform
- **Purpose**: Document signing and upload
- **Endpoint**: Configured via `esign.api.url`
- **Authentication**: Token via `esign.api.token`

### 4. Vision Archive System
- **Purpose**: Long-term document archiving
- **Endpoint**: Configured via `vision.api.url`
- **Retention**: 7 years compliance archiving

### 5. Contract Generator
- **Purpose**: Generate insurance contracts
- **Endpoint**: Configured via `contract.generator.url`

---

## 🔧 Configuration

All external API endpoints and credentials are configured in `application.yml`:

```yaml
external-apis:
  simulator:
    url: "https://simulator-api.company.com"
    api-key: "${SIMULATOR_API_KEY}"
  esign:
    url: "https://esign.company.com"
    token: "${ESIGN_TOKEN}"
  vision:
    url: "https://vision-archive.company.com"
    api-key: "${VISION_API_KEY}"
  contract-generator:
    url: "https://contract-gen.company.com"
    api-key: "${CONTRACT_API_KEY}"
```

---

## 📋 Workflow Process

1. **Form Submission** → Start process with customer data
2. **Simulator Integration** → Determine tariff conditions
3. **Tariff Decision** → Route based on SPECIFIC/STANDARD
4. **Client Choice** → Quote acceptance or modification
5. **Quote Loop** → Allow modifications until accepted
6. **E-Sign Upload** → Upload documents for signing
7. **Contract Generation** → Generate insurance contract
8. **Vision Archive** → Archive documents for compliance

---

## 🚀 Getting Started

1. Start the application: `./mvnw spring-boot:run`
2. Access Swagger UI: `http://localhost:8080/swagger-ui.html`
3. Submit a form via POST `/api/workflow/form-submission/start`
4. Monitor process via GET `/api/workflow/process/{id}/status`