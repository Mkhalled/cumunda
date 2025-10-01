package com.company.orchestrator.interfaces.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private static final String CUSTOMER_ID_KEY = "customerId";
    private static final String PROCESS_INSTANCE_ID_KEY = "processInstanceId";
    private static final String STATUS_KEY = "status";
    private static final String MESSAGE_KEY = "message";

    private final RuntimeService runtimeService;

    @PostMapping("/form-submission/start")
    public ResponseEntity<Map<String, Object>> startFormSubmissionProcess(@RequestBody FormSubmissionRequest request) {
        log.info("Starting form submission process for customer: {}", request.getCustomerName());
        
        try {
            // Prepare process variables
            Map<String, Object> variables = new HashMap<>();
            variables.put(CUSTOMER_ID_KEY, UUID.randomUUID().toString());
            variables.put("customerName", request.getCustomerName());
            variables.put("email", request.getEmail());
            variables.put("formData", request.getFormData());
            variables.put("requestedCoverage", request.getRequestedCoverage());
            variables.put("premium", request.getPremium());
            variables.put("documents", request.getDocuments());
            
            // Start the process instance
            ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                    "onboarding-process", 
                    variables
            );
            
            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put(PROCESS_INSTANCE_ID_KEY, processInstance.getId());
            response.put("processDefinitionId", processInstance.getProcessDefinitionId());
            response.put(CUSTOMER_ID_KEY, variables.get(CUSTOMER_ID_KEY));
            response.put(STATUS_KEY, "STARTED");
            response.put(MESSAGE_KEY, "Form submission process started successfully");
            
            log.info("Form submission process started successfully. ProcessInstanceId: {}", processInstance.getId());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error starting form submission process: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put(STATUS_KEY, "ERROR");
            errorResponse.put(MESSAGE_KEY, "Failed to start form submission process: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @GetMapping("/process/{processInstanceId}/status")
    public ResponseEntity<Map<String, Object>> getProcessStatus(@PathVariable String processInstanceId) {
        log.info("Getting status for process instance: {}", processInstanceId);
        
        try {
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            
            Map<String, Object> response = new HashMap<>();
            
            if (processInstance != null) {
                // Process is still running
                response.put(PROCESS_INSTANCE_ID_KEY, processInstanceId);
                response.put(STATUS_KEY, "RUNNING");
                
                // Get current activity
                var executions = runtimeService.getActiveActivityIds(processInstanceId);
                response.put("activeActivities", executions);
                
                // Get process variables
                Map<String, Object> variables = runtimeService.getVariables(processInstanceId);
                response.put("variables", variables);
                
            } else {
                // Process has ended - check historic instances
                response.put(PROCESS_INSTANCE_ID_KEY, processInstanceId);
                response.put(STATUS_KEY, "COMPLETED");
                response.put(MESSAGE_KEY, "Process instance has completed");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting process status: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put(STATUS_KEY, "ERROR");
            errorResponse.put(MESSAGE_KEY, "Failed to get process status: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    @PostMapping("/process/{processInstanceId}/complete-quote-modification")
    public ResponseEntity<Map<String, Object>> completeQuoteModification(
            @PathVariable String processInstanceId, 
            @RequestBody Map<String, Object> variables) {
        log.info("Completing quote modification for process instance: {}", processInstanceId);
        
        try {
            // Update process variables
            runtimeService.setVariables(processInstanceId, variables);
            
            Map<String, Object> response = new HashMap<>();
            response.put(PROCESS_INSTANCE_ID_KEY, processInstanceId);
            response.put(STATUS_KEY, "QUOTE_MODIFIED");
            response.put(MESSAGE_KEY, "Quote modification completed successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error completing quote modification: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put(STATUS_KEY, "ERROR");
            errorResponse.put(MESSAGE_KEY, "Failed to complete quote modification: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    public static class FormSubmissionRequest {
        private String customerName;
        private String email;
        private Map<String, Object> formData;
        private String requestedCoverage;
        private Double premium;
        private java.util.List<String> documents;
        
        // Getters and setters
        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        
        public Map<String, Object> getFormData() { return formData; }
        public void setFormData(Map<String, Object> formData) { this.formData = formData; }
        
        public String getRequestedCoverage() { return requestedCoverage; }
        public void setRequestedCoverage(String requestedCoverage) { this.requestedCoverage = requestedCoverage; }
        
        public Double getPremium() { return premium; }
        public void setPremium(Double premium) { this.premium = premium; }
        
        public java.util.List<String> getDocuments() { return documents; }
        public void setDocuments(java.util.List<String> documents) { this.documents = documents; }
    }
}