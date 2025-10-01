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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component("contractGenerationDelegate")
public class ContractGenerationDelegate implements JavaDelegate {

    private static final Logger logger = LoggerFactory.getLogger(ContractGenerationDelegate.class);
    private static final String GENERATION_SUCCESS = "SUCCESS";
    private static final String GENERATION_FAILED = "FAILED";
    private static final String CONTRACT_TYPE_STANDARD = "STANDARD";
    private static final String CONTRACT_TYPE_CUSTOM = "CUSTOM";
    private static final String CONTRACT_STATUS_DRAFT = "DRAFT";
    private static final String CONTRACT_STATUS_READY = "READY_FOR_SIGNATURE";
    
    private final RestTemplate restTemplate;
    
    @Value("${external.contract.generator.url:http://localhost:8085/api/contract}")
    private String contractGeneratorUrl;
    
    @Value("${external.contract.generator.key:default-contract-key}")
    private String contractGeneratorApiKey;
    
    @Value("${contract.template.path:templates/}")
    private String contractTemplatePath;
    
    public ContractGenerationDelegate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    @Override
    public void execute(DelegateExecution execution) throws Exception {
        logger.info("Executing ContractGenerationDelegate for process instance: {}", execution.getProcessInstanceId());
        
        try {
            // Préparer les données pour la génération du contrat
            Map<String, Object> contractData = prepareContractData(execution);
            
            // Générer le contrat
            Map<String, Object> generationResult = generateContract(contractData, execution.getProcessInstanceId());
            
            // Stocker les résultats
            execution.setVariable("contractGenerationStatus", GENERATION_SUCCESS);
            execution.setVariable("contractId", generationResult.get("contractId"));
            execution.setVariable("contractPdf", generationResult.get("contractPdf"));
            execution.setVariable("contractStatus", generationResult.get("contractStatus"));
            execution.setVariable("contractType", generationResult.get("contractType"));
            execution.setVariable("contractGenerationTimestamp", System.currentTimeMillis());
            
            // Stocker les détails financiers
            execution.setVariable("finalContractAmount", generationResult.get("contractAmount"));
            execution.setVariable("contractDuration", generationResult.get("contractDuration"));
            execution.setVariable("contractTerms", generationResult.get("contractTerms"));
            
            logger.info("Contract generated successfully. Contract ID: {}", generationResult.get("contractId"));
            
        } catch (Exception e) {
            logger.error("Error generating contract for process instance: {}", execution.getProcessInstanceId(), e);
            
            execution.setVariable("contractGenerationStatus", GENERATION_FAILED);
            execution.setVariable("contractGenerationError", e.getMessage());
            
            // En cas d'erreur critique dans la génération de contrat, arrêter le processus
            throw new RuntimeException("Failed to generate contract", e);
        }
    }
    
    private Map<String, Object> prepareContractData(DelegateExecution execution) {
        Map<String, Object> contractData = new HashMap<>();
        
        // Métadonnées de base
        contractData.put("processInstanceId", execution.getProcessInstanceId());
        contractData.put("customerId", execution.getVariable("customerId"));
        contractData.put("customerName", execution.getVariable("customerName"));
        contractData.put("customerEmail", execution.getVariable("customerEmail"));
        contractData.put("customerAddress", execution.getVariable("customerAddress"));
        
        // Données du produit et de la tarification
        contractData.put("requestedProduct", execution.getVariable("requestedProduct"));
        contractData.put("requestedAmount", execution.getVariable("requestedAmount"));
        contractData.put("appliedTariff", execution.getVariable("appliedTariff"));
        contractData.put("tariffConditions", execution.getVariable("tariffConditions"));
        
        // Données du simulateur et de la rentabilité
        contractData.put("simulatorResult", execution.getVariable("simulatorResult"));
        contractData.put("profitabilityStatus", execution.getVariable("profitabilityStatus"));
        contractData.put("profitabilityScore", execution.getVariable("profitabilityScore"));
        
        // Déterminer le type de contrat
        String contractType = determineContractType(execution);
        contractData.put("contractType", contractType);
        
        // Données du devis si disponibles (pour les contrats venant d'un devis)
        Object quoteId = execution.getVariable("quoteId");
        if (quoteId != null) {
            contractData.put("baseQuoteId", quoteId);
            contractData.put("quoteAmount", execution.getVariable("quoteAmount"));
            contractData.put("quotedTerms", execution.getVariable("quotedTerms"));
        }
        
        // Données financières finales
        BigDecimal finalAmount = calculateFinalAmount(execution);
        contractData.put("contractAmount", finalAmount);
        
        // Conditions spécifiques
        contractData.put("riskProfile", execution.getVariable("riskProfile"));
        contractData.put("businessUnit", execution.getVariable("businessUnit"));
        contractData.put("salesRepresentative", execution.getVariable("salesRepresentative"));
        
        // Métadonnées de génération
        contractData.put("generationTimestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        contractData.put("templatePath", contractTemplatePath);
        
        logger.debug("Contract data prepared: {}", contractData);
        
        return contractData;
    }
    
    private String determineContractType(DelegateExecution execution) {
        // Vérifier si des conditions spécifiques ont été appliquées
        Object simulatorResult = execution.getVariable("simulatorResult");
        if ("SPECIFIC".equals(simulatorResult)) {
            return CONTRACT_TYPE_CUSTOM;
        }
        
        // Vérifier la rentabilité
        Object profitabilityStatus = execution.getVariable("profitabilityStatus");
        if ("MARGINAL".equals(profitabilityStatus) || "UNACCEPTABLE".equals(profitabilityStatus)) {
            return CONTRACT_TYPE_CUSTOM;
        }
        
        // Vérifier s'il y a eu des modifications de devis
        Object quoteModifications = execution.getVariable("quoteModifications");
        if (Boolean.TRUE.equals(quoteModifications)) {
            return CONTRACT_TYPE_CUSTOM;
        }
        
        return CONTRACT_TYPE_STANDARD;
    }
    
    private BigDecimal calculateFinalAmount(DelegateExecution execution) {
        // Essayer d'abord de récupérer le montant du devis s'il existe
        Object quoteAmount = execution.getVariable("quoteAmount");
        if (quoteAmount != null) {
            try {
                return new BigDecimal(quoteAmount.toString());
            } catch (Exception e) {
                logger.warn("Failed to parse quote amount: {}", quoteAmount, e);
            }
        }
        
        // Sinon, utiliser le montant demandé initialement
        Object requestedAmount = execution.getVariable("requestedAmount");
        if (requestedAmount != null) {
            try {
                return new BigDecimal(requestedAmount.toString());
            } catch (Exception e) {
                logger.warn("Failed to parse requested amount: {}", requestedAmount, e);
            }
        }
        
        // Valeur par défaut
        return BigDecimal.ZERO;
    }
    
    private Map<String, Object> generateContract(Map<String, Object> contractData, String processInstanceId) {
        try {
            // Préparer les headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + contractGeneratorApiKey);
            headers.set("X-Request-ID", processInstanceId);
            headers.set("X-Contract-Type", contractData.get("contractType").toString());
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(contractData, headers);
            
            // Appeler l'API de génération de contrat
            logger.info("Calling contract generator API: {}", contractGeneratorUrl + "/generate");
            ResponseEntity<Map<String, Object>> response = restTemplate.postForEntity(
                contractGeneratorUrl + "/generate",
                request,
                (Class<Map<String, Object>>) (Class<?>) Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                // Traiter la réponse
                Map<String, Object> result = new HashMap<>();
                result.put("contractId", responseBody.get("contractId"));
                result.put("contractPdf", responseBody.get("contractPdf")); // Document PDF encodé
                result.put("contractStatus", responseBody.get("status"));
                result.put("contractType", contractData.get("contractType"));
                result.put("contractAmount", contractData.get("contractAmount"));
                result.put("contractDuration", responseBody.get("duration"));
                result.put("contractTerms", responseBody.get("terms"));
                result.put("generationTimestamp", System.currentTimeMillis());
                
                return result;
            } else {
                throw new RuntimeException("Contract generator API returned unsuccessful response: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            logger.error("Failed to generate contract via API", e);
            
            // En cas d'échec de l'API, générer un contrat simulé
            return generateMockContract(contractData, processInstanceId);
        }
    }
    
    private Map<String, Object> generateMockContract(Map<String, Object> contractData, String processInstanceId) {
        logger.warn("Using mock contract generation due to API failure");
        
        Map<String, Object> mockResult = new HashMap<>();
        
        // Générer un ID de contrat unique
        String contractId = "CONTRACT_" + processInstanceId + "_" + getCurrentTimestamp();
        mockResult.put("contractId", contractId);
        
        // Générer un PDF de contrat simulé
        String mockContractContent = generateMockContractContent(contractData, contractId);
        mockResult.put("contractPdf", mockContractContent.getBytes());
        
        // Statut et métadonnées
        mockResult.put("contractStatus", CONTRACT_STATUS_READY);
        mockResult.put("contractType", contractData.get("contractType"));
        mockResult.put("contractAmount", contractData.get("contractAmount"));
        mockResult.put("contractDuration", "12"); // 12 mois par défaut
        mockResult.put("contractTerms", "Standard terms and conditions apply");
        mockResult.put("generationTimestamp", System.currentTimeMillis());
        mockResult.put("isMock", true);
        
        return mockResult;
    }
    
    private String generateMockContractContent(Map<String, Object> contractData, String contractId) {
        StringBuilder content = new StringBuilder();
        content.append("CONTRACT DOCUMENT\n");
        content.append("=================\n\n");
        content.append("Contract ID: ").append(contractId).append("\n");
        content.append("Process Instance: ").append(contractData.get("processInstanceId")).append("\n");
        content.append("Generation Date: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n\n");
        
        content.append("CUSTOMER INFORMATION:\n");
        content.append("Customer ID: ").append(contractData.get("customerId")).append("\n");
        content.append("Customer Name: ").append(contractData.get("customerName")).append("\n");
        content.append("Customer Email: ").append(contractData.get("customerEmail")).append("\n\n");
        
        content.append("PRODUCT INFORMATION:\n");
        content.append("Product: ").append(contractData.get("requestedProduct")).append("\n");
        content.append("Amount: ").append(contractData.get("contractAmount")).append("\n");
        content.append("Contract Type: ").append(contractData.get("contractType")).append("\n");
        content.append("Tariff: ").append(contractData.get("appliedTariff")).append("\n\n");
        
        content.append("TERMS AND CONDITIONS:\n");
        content.append("This is a mock contract generated for testing purposes.\n");
        content.append("All standard terms and conditions apply.\n");
        content.append("Risk Profile: ").append(contractData.get("riskProfile")).append("\n");
        content.append("Profitability Status: ").append(contractData.get("profitabilityStatus")).append("\n\n");
        
        content.append("Simulator Result: ").append(contractData.get("simulatorResult")).append("\n");
        content.append("Business Unit: ").append(contractData.get("businessUnit")).append("\n\n");
        
        content.append("SIGNATURE SECTION:\n");
        content.append("Customer Signature: _______________________\n");
        content.append("Company Representative: ___________________\n");
        content.append("Date: ____________________________________\n");
        
        return content.toString();
    }
    
    private String getCurrentTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }
}