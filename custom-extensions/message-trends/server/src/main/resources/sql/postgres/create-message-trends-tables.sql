-- PostgreSQL Migration script for Message Trends Management System

CREATE TABLE message_statistics_timeseries (
	id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	
	channel_id VARCHAR(36) NOT NULL,
	connector_id VARCHAR(36) NOT NULL, -- use '__EMPTY__' for channel-level
	ts TIMESTAMP WITHOUT TIME ZONE NOT NULL,
	bucket_size_minutes INTEGER NOT NULL,
	
	received INTEGER NOT NULL DEFAULT 0,
	filtered INTEGER NOT NULL DEFAULT 0,
	queued INTEGER NOT NULL DEFAULT 0,
	sent INTEGER NOT NULL DEFAULT 0,
	error INTEGER NOT NULL DEFAULT 0,
	
	server_id VARCHAR(36) NOT NULL,
		
	CONSTRAINT uq_mstats_key UNIQUE (server_id, channel_id, connector_id, bucket_size_minutes, ts)
);

CREATE INDEX IF NOT EXISTS idx_mstats_server_channel_connector_bucket_ts ON message_statistics_timeseries (server_id, channel_id, connector_id, bucket_size_minutes, ts);
CREATE INDEX IF NOT EXISTS idx_mstats_server_bucket_ts ON message_statistics_timeseries (server_id, bucket_size_minutes, ts);