-- SQL Server Migration script for Message Trends Management System

CREATE TABLE message_statistics_timeseries (
	id BIGINT IDENTITY(1,1) PRIMARY KEY,
	
	channel_id VARCHAR(36) NOT NULL,
	connector_id VARCHAR(36) NOT NULL, -- use '' for channel-level
	ts DATETIME2(0) NOT NULL,
	bucket_size_minutes INT NOT NULL,
		
	received INT NOT NULL CONSTRAINT df_mst_received DEFAULT 0,
	filtered INT NOT NULL CONSTRAINT df_mst_filtered DEFAULT 0,
	queued INT NOT NULL CONSTRAINT df_mst_queued DEFAULT 0,
	sent INT NOT NULL CONSTRAINT df_mst_sent DEFAULT 0,
	error INT NOT NULL CONSTRAINT df_mst_error DEFAULT 0,
	
	server_id VARCHAR(36) NOT NULL,
	
	CONSTRAINT uq_mstats_key UNIQUE (server_id, channel_id, connector_id, ts, bucket_size_minutes)
);

CREATE INDEX idx_mstats_bucket_time ON message_statistics_timeseries (bucket_size_minutes, ts, channel_id, connector_id);
CREATE INDEX idx_mstats_server_time ON message_statistics_timeseries (server_id, ts, bucket_size_minutes);
CREATE INDEX idx_mstats_channel_time ON message_statistics_timeseries (channel_id, ts);
CREATE INDEX idx_mstats_connector_time ON message_statistics_timeseries (channel_id, connector_id, ts);
