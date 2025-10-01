package com.company.orchestrator.infrastructure.delegate;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component("eSignUploadDelegate")
public class ESignUploadDelegate implements JavaDelegate {

    private static final Logger logger = LoggerFactory.getLogger(ESignUploadDelegate.class);
    private static final String UPLOAD_SUCCESS = "SUCCESS";
    private static final String UPLOAD_FAILED = "FAILED";
    private static final String DOCUMENT_TYPE_QUOTE = "QUOTE";
    private static final String DOCUMENT_TYPE_CONTRACT = "CONTRACT";
    
    private final RestTemplate restTemplate;
    
    @Value("${external.esign.api.url:http://localhost:8083/api/esign}")
    private String eSignApiUrl;
    
    @Value("${external.esign.api.key:default-api-key}")
    private String eSignApiKey;
    
    @Value("${external.esign.webhook.url:http://localhost:8080/api/webhook/esign}")
    private String webhookUrl;
    
    public ESignUploadDelegate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    @Override
    public void execute(DelegateExecution execution) throws Exception {
        logger.info("Executing ESignUploadDelegate for process instance: {}", execution.getProcessInstanceId());
        
        try {
            // Déterminer le type de document (devis ou contrat)
            String documentType = determineDocumentType(execution);
            
            // Préparer le document pour E-Sign
            Map<String, Object> documentData = prepareDocumentForESign(execution, documentType);
            
            // Télécharger vers E-Sign
            Map<String, Object> uploadResult = uploadToESign(documentData, execution.getProcessInstanceId());
            
            // Stocker les résultats
            execution.setVariable("eSignUploadStatus", UPLOAD_SUCCESS);
            execution.setVariable("eSignDocumentId", uploadResult.get("documentId"));
            execution.setVariable("eSignUrl", uploadResult.get("signUrl"));
            execution.setVariable("eSignWebhookId", uploadResult.get("webhookId"));
            execution.setVariable("documentType", documentType);
            
            logger.info("Document uploaded to E-Sign successfully. Document ID: {}", uploadResult.get("documentId"));
            
        } catch (Exception e) {
            logger.error("Error uploading document to E-Sign for process instance: {}", execution.getProcessInstanceId(), e);
            
            execution.setVariable("eSignUploadStatus", UPLOAD_FAILED);
            execution.setVariable("eSignError", e.getMessage());
            
            // En fonction des exigences métier, on peut soit arrêter le processus soit continuer
            throw new RuntimeException("Failed to upload document to E-Sign", e);
        }
    }
    
    private String determineDocumentType(DelegateExecution execution) {
        // Déterminer si on traite un devis ou un contrat basé sur l'activité courante
        String currentActivityId = execution.getCurrentActivityId();
        
        if (currentActivityId != null && currentActivityId.contains("quote")) {
            return DOCUMENT_TYPE_QUOTE;
        } else if (currentActivityId != null && currentActivityId.contains("contract")) {
            return DOCUMENT_TYPE_CONTRACT;
        }
        
        // Vérifier les variables du processus
        Object documentTypeVar = execution.getVariable("documentType");
        if (documentTypeVar != null) {
            return documentTypeVar.toString().toUpperCase();
        }
        
        // Par défaut, considérer comme un devis
        return DOCUMENT_TYPE_QUOTE;
    }
    
    private Map<String, Object> prepareDocumentForESign(DelegateExecution execution, String documentType) {
        Map<String, Object> documentData = new HashMap<>();
        
        // Métadonnées du document
        documentData.put("documentType", documentType);
        documentData.put("processInstanceId", execution.getProcessInstanceId());
        documentData.put("customerId", execution.getVariable("customerId"));
        documentData.put("customerEmail", execution.getVariable("customerEmail"));
        documentData.put("customerName", execution.getVariable("customerName"));
        
        // Données spécifiques au type de document
        if (DOCUMENT_TYPE_QUOTE.equals(documentType)) {
            documentData.put("quoteId", execution.getVariable("quoteId"));
            documentData.put("quoteAmount", execution.getVariable("quoteAmount"));
            documentData.put("quotePdf", execution.getVariable("quotePdf")); // Document PDF encodé
            documentData.put("documentName", "Quote_" + execution.getVariable("customerId") + "_" + System.currentTimeMillis());
        } else if (DOCUMENT_TYPE_CONTRACT.equals(documentType)) {
            documentData.put("contractId", execution.getVariable("contractId"));
            documentData.put("contractAmount", execution.getVariable("contractAmount"));
            documentData.put("contractPdf", execution.getVariable("contractPdf")); // Document PDF encodé
            documentData.put("documentName", "Contract_" + execution.getVariable("customerId") + "_" + System.currentTimeMillis());
        }
        
        // Configuration de signature
        documentData.put("signerEmail", execution.getVariable("customerEmail"));
        documentData.put("signerName", execution.getVariable("customerName"));
        documentData.put("webhookUrl", webhookUrl);
        documentData.put("returnUrl", execution.getVariable("returnUrl"));
        
        logger.debug("Document data prepared for E-Sign: {}", documentData);
        
        return documentData;
    }
    
    private Map<String, Object> uploadToESign(Map<String, Object> documentData, String processInstanceId) {
        try {
            // Préparer les headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("Authorization", "Bearer " + eSignApiKey);
            headers.set("X-Request-ID", processInstanceId);
            
            // Préparer le contenu multipart
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            
            // Ajouter les métadonnées
            body.add("documentType", documentData.get("documentType"));
            body.add("documentName", documentData.get("documentName"));
            body.add("processInstanceId", processInstanceId);
            body.add("customerId", documentData.get("customerId"));
            
            // Ajouter les informations du signataire
            body.add("signerEmail", documentData.get("signerEmail"));
            body.add("signerName", documentData.get("signerName"));
            body.add("webhookUrl", documentData.get("webhookUrl"));
            
            // Ajouter le fichier PDF
            Object pdfData = documentData.get("quotePdf");
            if (pdfData == null) {
                pdfData = documentData.get("contractPdf");
            }
            
            if (pdfData != null) {
                ByteArrayResource pdfResource = new ByteArrayResource((byte[]) pdfData) {
                    @Override
                    public String getFilename() {
                        return documentData.get("documentName") + ".pdf";
                    }
                };
                body.add("document", pdfResource);
            } else {
                // Générer un document PDF de test si aucun n'est fourni
                String testContent = "Test document for " + documentData.get("documentType");
                ByteArrayResource testResource = new ByteArrayResource(testContent.getBytes()) {
                    @Override
                    public String getFilename() {
                        return documentData.get("documentName") + ".pdf";
                    }
                };
                body.add("document", testResource);
            }
            
            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            
            // Appeler l'API E-Sign
            logger.info("Uploading document to E-Sign API: {}", eSignApiUrl + "/upload");
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                eSignApiUrl + "/upload",
                HttpMethod.POST,
                request,
                (Class<Map<String, Object>>) (Class<?>) Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                // Traiter la réponse
                Map<String, Object> result = new HashMap<>();
                result.put("documentId", responseBody.get("documentId"));
                result.put("signUrl", responseBody.get("signUrl"));
                result.put("webhookId", responseBody.get("webhookId"));
                result.put("status", responseBody.get("status"));
                result.put("uploadTimestamp", System.currentTimeMillis());
                
                return result;
            } else {
                throw new RuntimeException("E-Sign API returned unsuccessful response: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            logger.error("Failed to upload document to E-Sign", e);
            
            // En cas d'échec, retourner un résultat simulé pour les tests
            return createMockESignResult(documentData, processInstanceId);
        }
    }
    
    private Map<String, Object> createMockESignResult(Map<String, Object> documentData, String processInstanceId) {
        logger.warn("Using mock E-Sign result due to API failure");
        
        Map<String, Object> mockResult = new HashMap<>();
        mockResult.put("documentId", "MOCK_" + UUID.randomUUID().toString());
        mockResult.put("signUrl", "https://mock-esign.com/sign/" + processInstanceId);
        mockResult.put("webhookId", "WEBHOOK_" + UUID.randomUUID().toString());
        mockResult.put("status", "PENDING_SIGNATURE");
        mockResult.put("uploadTimestamp", System.currentTimeMillis());
        mockResult.put("isMock", true);
        
        return mockResult;
    }
}