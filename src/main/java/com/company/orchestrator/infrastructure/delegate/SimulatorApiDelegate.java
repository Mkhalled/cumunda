package com.company.orchestrator.infrastructure.delegate;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component("simulatorApiDelegate")
public class SimulatorApiDelegate implements JavaDelegate {

    private static final Logger logger = LoggerFactory.getLogger(SimulatorApiDelegate.class);
    
    private final RestTemplate restTemplate;
    
    @Value("${external.simulator.api.url:http://localhost:8081/api/simulator}")
    private String simulatorApiUrl;
    
    @Value("${external.simulator.api.timeout:5000}")
    private int timeout;
    
    public SimulatorApiDelegate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    @Override
    public void execute(DelegateExecution execution) throws Exception {
        logger.info("Executing SimulatorApiDelegate for process instance: {}", execution.getProcessInstanceId());
        
        try {
            // Récupérer les données du formulaire depuis les variables du processus
            Map<String, Object> formData = extractFormData(execution);
            
            // Préparer la requête pour l'API simulateur
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Request-ID", execution.getProcessInstanceId());
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(formData, headers);
            
            // Appeler l'API simulateur externe
            logger.info("Calling external simulator API: {}", simulatorApiUrl);
            ResponseEntity<Map> response = restTemplate.postForEntity(simulatorApiUrl, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                // Extraire le résultat du simulateur
                String simulatorResult = extractSimulatorResult(responseBody);
                
                // Stocker le résultat dans les variables du processus
                execution.setVariable("simulatorResult", simulatorResult);
                execution.setVariable("simulatorResponse", responseBody);
                execution.setVariable("simulatorApiCallSuccess", true);
                
                logger.info("Simulator API call successful. Result: {}", simulatorResult);
                
            } else {
                throw new RuntimeException("Simulator API returned unsuccessful response: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            logger.error("Error calling simulator API for process instance: {}", execution.getProcessInstanceId(), e);
            
            // En cas d'erreur, utiliser une valeur par défaut
            execution.setVariable("simulatorResult", "STANDARD");
            execution.setVariable("simulatorApiCallSuccess", false);
            execution.setVariable("simulatorError", e.getMessage());
            
            // Optionnel: relancer l'exception si on veut arrêter le processus
            // throw new RuntimeException("Failed to call simulator API", e);
        }
    }
    
    private Map<String, Object> extractFormData(DelegateExecution execution) {
        Map<String, Object> formData = new HashMap<>();
        
        // Récupérer les données du formulaire depuis les variables du processus
        formData.put("customerId", execution.getVariable("customerId"));
        formData.put("customerType", execution.getVariable("customerType"));
        formData.put("requestedAmount", execution.getVariable("requestedAmount"));
        formData.put("requestedProduct", execution.getVariable("requestedProduct"));
        formData.put("riskProfile", execution.getVariable("riskProfile"));
        formData.put("customerData", execution.getVariable("customerData"));
        formData.put("formSubmissionId", execution.getVariable("formSubmissionId"));
        formData.put("submissionTimestamp", execution.getVariable("submissionTimestamp"));
        
        // Ajouter des métadonnées du processus
        formData.put("processInstanceId", execution.getProcessInstanceId());
        formData.put("activityId", execution.getCurrentActivityId());
        
        logger.debug("Form data extracted for simulator API: {}", formData);
        
        return formData;
    }
    
    private String extractSimulatorResult(Map<String, Object> responseBody) {
        // Extraire le résultat du simulateur depuis la réponse
        Object result = responseBody.get("result");
        Object recommendation = responseBody.get("recommendation");
        Object tariffType = responseBody.get("tariffType");
        
        // Logique pour déterminer si des conditions spécifiques sont requises
        if (result != null) {
            String resultStr = result.toString().toUpperCase();
            if ("SPECIFIC".equals(resultStr) || "SPECIAL".equals(resultStr) || "CUSTOM".equals(resultStr)) {
                return "SPECIFIC";
            }
        }
        
        if (recommendation != null) {
            String recStr = recommendation.toString().toLowerCase();
            if (recStr.contains("special") || recStr.contains("specific") || recStr.contains("custom")) {
                return "SPECIFIC";
            }
        }
        
        if (tariffType != null) {
            String tariffStr = tariffType.toString().toUpperCase();
            if ("SPECIFIC".equals(tariffStr) || "CUSTOM".equals(tariffStr)) {
                return "SPECIFIC";
            }
        }
        
        // Par défaut, retourner STANDARD
        return "STANDARD";
    }
}