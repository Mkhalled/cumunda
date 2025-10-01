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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component("profitabilitySimulatorDelegate")
public class ProfitabilitySimulatorDelegate implements JavaDelegate {

    private static final Logger logger = LoggerFactory.getLogger(ProfitabilitySimulatorDelegate.class);
    private static final String PROFITABILITY_ACCEPTABLE = "ACCEPTABLE";
    private static final String PROFITABILITY_MARGINAL = "MARGINAL";
    private static final String PROFITABILITY_UNACCEPTABLE = "UNACCEPTABLE";
    
    private final RestTemplate restTemplate;
    
    @Value("${external.profitability.api.url:http://localhost:8082/api/profitability}")
    private String profitabilityApiUrl;
    
    @Value("${profitability.threshold.minimum:0.05}")
    private BigDecimal minimumProfitabilityThreshold;
    
    @Value("${profitability.threshold.target:0.15}")
    private BigDecimal targetProfitabilityThreshold;
    
    public ProfitabilitySimulatorDelegate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    @Override
    public void execute(DelegateExecution execution) throws Exception {
        logger.info("Executing ProfitabilitySimulatorDelegate for process instance: {}", execution.getProcessInstanceId());
        
        try {
            // Préparer les données pour l'analyse de rentabilité
            Map<String, Object> profitabilityData = prepareProfitabilityData(execution);
            
            // Appeler l'API de simulation de rentabilité
            Map<String, Object> profitabilityResult = callProfitabilityApi(profitabilityData, execution.getProcessInstanceId());
            
            // Analyser les résultats
            String profitabilityStatus = analyzeProfitability(profitabilityResult);
            BigDecimal profitabilityScore = extractProfitabilityScore(profitabilityResult);
            
            // Stocker les résultats dans les variables du processus
            execution.setVariable("profitabilityStatus", profitabilityStatus);
            execution.setVariable("profitabilityScore", profitabilityScore);
            execution.setVariable("profitabilityResult", profitabilityResult);
            execution.setVariable("profitabilityCheckSuccess", true);
            
            logger.info("Profitability analysis completed. Status: {}, Score: {}", profitabilityStatus, profitabilityScore);
            
        } catch (Exception e) {
            logger.error("Error during profitability analysis for process instance: {}", execution.getProcessInstanceId(), e);
            
            // En cas d'erreur, utiliser des valeurs par défaut conservatrices
            execution.setVariable("profitabilityStatus", PROFITABILITY_MARGINAL);
            execution.setVariable("profitabilityScore", minimumProfitabilityThreshold);
            execution.setVariable("profitabilityCheckSuccess", false);
            execution.setVariable("profitabilityError", e.getMessage());
            
            logger.warn("Using default profitability values due to error");
        }
    }
    
    private Map<String, Object> prepareProfitabilityData(DelegateExecution execution) {
        Map<String, Object> data = new HashMap<>();
        
        // Données du client et du produit
        data.put("customerId", execution.getVariable("customerId"));
        data.put("requestedAmount", execution.getVariable("requestedAmount"));
        data.put("requestedProduct", execution.getVariable("requestedProduct"));
        data.put("riskProfile", execution.getVariable("riskProfile"));
        
        // Données de tarification
        data.put("simulatorResult", execution.getVariable("simulatorResult"));
        data.put("appliedTariff", execution.getVariable("appliedTariff"));
        data.put("tariffConditions", execution.getVariable("tariffConditions"));
        
        // Données financières
        data.put("expectedRevenue", execution.getVariable("expectedRevenue"));
        data.put("estimatedCosts", execution.getVariable("estimatedCosts"));
        data.put("contractDuration", execution.getVariable("contractDuration"));
        
        // Métadonnées
        data.put("processInstanceId", execution.getProcessInstanceId());
        data.put("analysisTimestamp", System.currentTimeMillis());
        
        logger.debug("Profitability data prepared: {}", data);
        
        return data;
    }
    
    private Map<String, Object> callProfitabilityApi(Map<String, Object> data, String processInstanceId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Request-ID", processInstanceId);
            headers.set("X-Analysis-Type", "CONTRACT_PROFITABILITY");
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(data, headers);
            
            logger.info("Calling profitability API: {}", profitabilityApiUrl);
            ResponseEntity<Map<String, Object>> response = restTemplate.postForEntity(
                profitabilityApiUrl, request, (Class<Map<String, Object>>) (Class<?>) Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                throw new RuntimeException("Profitability API returned unsuccessful response: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            logger.warn("Profitability API call failed, using fallback calculation", e);
            return performFallbackCalculation(data);
        }
    }
    
    private Map<String, Object> performFallbackCalculation(Map<String, Object> data) {
        Map<String, Object> fallbackResult = new HashMap<>();
        
        // Calcul de rentabilité simplifié en cas d'échec de l'API
        Object revenueObj = data.get("expectedRevenue");
        Object costsObj = data.get("estimatedCosts");
        
        if (revenueObj != null && costsObj != null) {
            try {
                BigDecimal revenue = new BigDecimal(revenueObj.toString());
                BigDecimal costs = new BigDecimal(costsObj.toString());
                
                if (revenue.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal profit = revenue.subtract(costs);
                    BigDecimal profitabilityRatio = profit.divide(revenue, 4, BigDecimal.ROUND_HALF_UP);
                    
                    fallbackResult.put("profitabilityRatio", profitabilityRatio);
                    fallbackResult.put("calculationMethod", "FALLBACK");
                    fallbackResult.put("estimatedProfit", profit);
                } else {
                    fallbackResult.put("profitabilityRatio", BigDecimal.ZERO);
                }
            } catch (Exception e) {
                logger.warn("Fallback calculation failed", e);
                fallbackResult.put("profitabilityRatio", minimumProfitabilityThreshold);
            }
        } else {
            fallbackResult.put("profitabilityRatio", minimumProfitabilityThreshold);
        }
        
        fallbackResult.put("calculationMethod", "FALLBACK");
        
        return fallbackResult;
    }
    
    private String analyzeProfitability(Map<String, Object> result) {
        BigDecimal profitabilityScore = extractProfitabilityScore(result);
        
        if (profitabilityScore.compareTo(targetProfitabilityThreshold) >= 0) {
            return PROFITABILITY_ACCEPTABLE;
        } else if (profitabilityScore.compareTo(minimumProfitabilityThreshold) >= 0) {
            return PROFITABILITY_MARGINAL;
        } else {
            return PROFITABILITY_UNACCEPTABLE;
        }
    }
    
    private BigDecimal extractProfitabilityScore(Map<String, Object> result) {
        Object scoreObj = result.get("profitabilityRatio");
        if (scoreObj != null) {
            try {
                return new BigDecimal(scoreObj.toString());
            } catch (Exception e) {
                logger.warn("Failed to parse profitability score: {}", scoreObj, e);
            }
        }
        
        // Valeur par défaut
        return minimumProfitabilityThreshold;
    }
}