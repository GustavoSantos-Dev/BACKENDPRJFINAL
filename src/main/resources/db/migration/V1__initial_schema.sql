-- MySQL 8.0.16+; UTC em DATETIME(6), UUID em BINARY(16), motor transacional InnoDB.
CREATE TABLE app_users (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(120) NOT NULL, email VARCHAR(254) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL, analytics_password_hash VARCHAR(100),
 role VARCHAR(20) NOT NULL CHECK(role IN ('OPERADOR','ANALISTA','ADMIN')), active BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE screw_types (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(120) NOT NULL, description VARCHAR(1000) NOT NULL,
 photo_url VARCHAR(1000), specifications VARCHAR(2000), active BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE devices (
 id VARCHAR(64) PRIMARY KEY, name VARCHAR(120) NOT NULL, api_key_hash VARCHAR(64) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE production_batches (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, operation_id VARCHAR(64) NOT NULL UNIQUE,
 target BIGINT NOT NULL CHECK(target > 0), current_count BIGINT NOT NULL DEFAULT 0 CHECK(current_count >= 0),
 status VARCHAR(20) NOT NULL CHECK(status IN ('CONFIGURADO','EM_EXECUCAO','PAUSADO','FINALIZADO')),
 screw_type_id BIGINT NOT NULL, operator_id BIGINT NOT NULL, device_id VARCHAR(64) NOT NULL,
 created_at DATETIME(6) NOT NULL, started_at DATETIME(6), finished_at DATETIME(6), version BIGINT NOT NULL DEFAULT 0,
 open_device_id VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN status IN ('EM_EXECUCAO','PAUSADO') THEN device_id ELSE NULL END) STORED,
 CONSTRAINT fk_batch_screw FOREIGN KEY (screw_type_id) REFERENCES screw_types(id),
 CONSTRAINT fk_batch_operator FOREIGN KEY (operator_id) REFERENCES app_users(id),
 CONSTRAINT fk_batch_device FOREIGN KEY (device_id) REFERENCES devices(id),
 CONSTRAINT uq_device_open_batch UNIQUE(open_device_id),
 CHECK(finished_at IS NULL OR (started_at IS NOT NULL AND finished_at >= started_at)),
 CHECK(status = 'CONFIGURADO' OR started_at IS NOT NULL), CHECK((status = 'FINALIZADO') = (finished_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE INDEX idx_batch_created ON production_batches(created_at);
CREATE INDEX idx_batch_finished ON production_batches(finished_at);
CREATE INDEX idx_batch_operator ON production_batches(operator_id);
CREATE INDEX idx_batch_screw ON production_batches(screw_type_id);
CREATE TABLE production_events (
 event_id BINARY(16) PRIMARY KEY, batch_id BIGINT NOT NULL, device_id VARCHAR(64) NOT NULL,
 detected_at DATETIME(6) NOT NULL, received_at DATETIME(6) NOT NULL, count_after BIGINT NOT NULL CHECK(count_after > 0),
 CONSTRAINT fk_event_batch FOREIGN KEY (batch_id) REFERENCES production_batches(id),
 CONSTRAINT fk_event_device FOREIGN KEY (device_id) REFERENCES devices(id),
 UNIQUE(batch_id,count_after)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE INDEX idx_event_batch_received ON production_events(batch_id,received_at DESC);
CREATE INDEX idx_event_detected ON production_events(detected_at);
CREATE INDEX idx_event_device ON production_events(device_id);
CREATE TABLE production_pauses (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, batch_id BIGINT NOT NULL, paused_by BIGINT NOT NULL, resumed_by BIGINT,
 paused_at DATETIME(6) NOT NULL, resumed_at DATETIME(6),
 open_batch_id BIGINT GENERATED ALWAYS AS (CASE WHEN resumed_at IS NULL THEN batch_id ELSE NULL END) STORED,
 CONSTRAINT fk_pause_batch FOREIGN KEY (batch_id) REFERENCES production_batches(id),
 CONSTRAINT fk_pause_actor FOREIGN KEY (paused_by) REFERENCES app_users(id),
 CONSTRAINT fk_resume_actor FOREIGN KEY (resumed_by) REFERENCES app_users(id),
 CONSTRAINT uq_batch_open_pause UNIQUE(open_batch_id),
 CHECK(resumed_at IS NULL OR resumed_at >= paused_at), CHECK((resumed_at IS NULL) = (resumed_by IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE INDEX idx_pause_batch_time ON production_pauses(batch_id,paused_at);
