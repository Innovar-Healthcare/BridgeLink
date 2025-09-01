-- PostgreSQL Migration script for Message Trends Management System

CREATE TABLE message_statistics_timeseries (
	id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	
	channel_id VARCHAR(36) NOT NULL,
	connector_id VARCHAR(36) NOT NULL, -- use '' for channel-level
	ts TIMESTAMP WITHOUT TIME ZONE NOT NULL,
	bucket_size_minutes INTEGER NOT NULL,
	
	received INTEGER NOT NULL DEFAULT 0,
	filtered INTEGER NOT NULL DEFAULT 0,
	queued INTEGER NOT NULL DEFAULT 0,
	sent INTEGER NOT NULL DEFAULT 0,
	error INTEGER NOT NULL DEFAULT 0,
	
	server_id VARCHAR(36) NOT NULL,
		
	CONSTRAINT uq_mstats_key UNIQUE (server_id, channel_id, connector_id, ts, bucket_size_minutes)
);

CREATE INDEX idx_mstats_bucket_time ON message_statistics_timeseries (bucket_size_minutes, ts, channel_id, connector_id);
CREATE INDEX idx_mstats_server_time ON message_statistics_timeseries (server_id, ts, bucket_size_minutes);
CREATE INDEX idx_mstats_channel_time ON message_statistics_timeseries (channel_id, ts);
CREATE INDEX idx_mstats_connector_time ON message_statistics_timeseries (channel_id, connector_id, ts);