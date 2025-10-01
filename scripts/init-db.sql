-- Initialize database for Camunda and application
CREATE SCHEMA IF NOT EXISTS camunda;
CREATE SCHEMA IF NOT EXISTS app;

-- Grant permissions
GRANT ALL PRIVILEGES ON SCHEMA camunda TO orchestrator_user;
GRANT ALL PRIVILEGES ON SCHEMA app TO orchestrator_user;

-- Create application-specific tables if needed
CREATE TABLE IF NOT EXISTS app.process_audit (
    id BIGSERIAL PRIMARY KEY,
    process_instance_id VARCHAR(64) NOT NULL,
    customer_id VARCHAR(255),
    process_key VARCHAR(255),
    start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    end_time TIMESTAMP,
    status VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index for performance
CREATE INDEX IF NOT EXISTS idx_process_audit_instance_id ON app.process_audit(process_instance_id);
CREATE INDEX IF NOT EXISTS idx_process_audit_customer_id ON app.process_audit(customer_id);