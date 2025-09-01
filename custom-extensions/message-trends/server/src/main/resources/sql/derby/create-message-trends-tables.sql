-- Derby Migration script for Message Trends Management System

CREATE TABLE message_statistics_timeseries (
    id BIGINT GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1) PRIMARY KEY,
    
    channel_id VARCHAR(36) NOT NULL,
	connector_id VARCHAR(36) NOT NULL, -- use '' for channel-level
	ts TIMESTAMP NOT NULL,
	bucket_size_minutes INT NOT NULL,
	
	received INTEGER NOT NULL DEFAULT 0,
	filtered INTEGER NOT NULL DEFAULT 0,
	queued INTEGER NOT NULL DEFAULT 0,
	sent INTEGER NOT NULL DEFAULT 0,
	error INTEGER NOT NULL DEFAULT 0,
		
	server_id VARCHAR(36) NOT NULL
);

CREATE UNIQUE INDEX uq_mstats_key ON message_statistics_timeseries (server_id, channel_id, connector_id, ts, bucket_size_minutes);
CREATE INDEX idx_mstats_bucket_time ON message_statistics_timeseries (bucket_size_minutes, ts, channel_id, connector_id);
CREATE INDEX idx_mstats_server_time ON message_statistics_timeseries (server_id, ts, bucket_size_minutes);
CREATE INDEX idx_mstats_channel_time ON message_statistics_timeseries (channel_id, ts);
CREATE INDEX idx_mstats_connector_time ON message_statistics_timeseries (channel_id, connector_id, ts);
