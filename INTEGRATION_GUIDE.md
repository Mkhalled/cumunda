# Integration Guide - Form Submission Workflow

## Quick Start

This guide helps you integrate with the Form Submission Workflow system for insurance quote processing.

---

## 🚀 Basic Integration

### 1. Start a New Process

```bash
curl -X POST http://localhost:8080/api/workflow/form-submission/start \
  -H "Content-Type: application/json" \
  -d '{
    "customerName": "John Doe",
    "email": "john.doe@example.com",
    "requestedCoverage": "FULL",
    "premium": 150.0,
    "formData": {
      "age": 35,
      "profession": "Engineer"
    }
  }'
```

### 2. Check Process Status

```bash
curl -X GET http://localhost:8080/api/workflow/process/{processInstanceId}/status
```

### 3. Complete Quote Modification

```bash
curl -X POST http://localhost:8080/api/workflow/process/{processInstanceId}/complete-quote-modification \
  -H "Content-Type: application/json" \
  -d '{
    "modifiedPremium": 200.0,
    "acceptedQuote": true
  }'
```

---

## 🔧 Configuration

### Environment Variables

Set these environment variables for external API integration:

```bash
export SIMULATOR_API_KEY="your-simulator-key"
export ESIGN_TOKEN="your-esign-token"
export VISION_API_KEY="your-vision-key"
export CONTRACT_API_KEY="your-contract-key"
```

### Mock Mode

For development/testing, enable mock mode:

```yaml
workflow:
  mock-mode: true
```

---

## 🔌 Webhook Integration

### Process Completion Webhook

Configure a webhook to receive notifications when processes complete:

```yaml
webhook:
  url: "https://your-system.com/webhook/process-complete"
  events:
    - PROCESS_COMPLETED
    - DOCUMENT_SIGNED
    - CONTRACT_GENERATED
```

---

## 🧪 Testing with Postman

Import the following Postman collection:

```json
{
  "name": "Form Submission Workflow",
  "requests": [
    {
      "name": "Start Process",
      "method": "POST",
      "url": "{{baseUrl}}/api/workflow/form-submission/start",
      "body": {
        "customerName": "Test Customer",
        "email": "test@example.com",
        "requestedCoverage": "BASIC",
        "premium": 100.0
      }
    }
  ]
}
```

---

## 🔍 Troubleshooting

### Common Issues

1. **Process not starting**: Check if all required fields are provided
2. **External API errors**: Verify API keys and endpoints in configuration
3. **Process stuck**: Check Camunda Cockpit at `http://localhost:8080/camunda`

### Mock Servers

For testing, use mock servers for external APIs:

```javascript
// Simple mock server with Node.js
const express = require('express');
const app = express();

app.post('/simulator/analyze', (req, res) => {
  res.json({ tariffCondition: 'SPECIFIC' });
});

app.listen(3001, () => {
  console.log('Mock simulator running on port 3001');
});
```

---

## 📞 Support

For integration support, check the README.md file or contact the development team.