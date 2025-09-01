-- MySQL Migration script for Message Trends Management System

CREATE TABLE message_statistics_timeseries (
	id BIGINT NOT NULL AUTO_INCREMENT,
	
	channel_id VARCHAR(36) NOT NULL,
	connector_id VARCHAR(36) NOT NULL, -- '' for channel-level
	ts DATETIME(0) NOT NULL,
	bucket_size_minutes INT NOT NULL,
	
	received INT NOT NULL DEFAULT 0,
	filtered INT NOT NULL DEFAULT 0,
	queued INT NOT NULL DEFAULT 0,
	sent INT NOT NULL DEFAULT 0,
	error INT NOT NULL DEFAULT 0,
	
	server_id VARCHAR(36) NOT NULL,
	
	PRIMARY KEY (id),
	
	UNIQUE KEY uq_mstats_key (server_id, channel_id, connector_id, ts, bucket_size_minutes),
	
	KEY idx_mstats_bucket_time (bucket_size_minutes, ts, channel_id, connector_id),
	KEY idx_mstats_server_time (server_id, ts, bucket_size_minutes),
	KEY idx_mstats_channel_time (channel_id, ts),
	KEY idx_mstats_connector_time (channel_id, connector_id, ts)
) ENGINE=InnoDB;