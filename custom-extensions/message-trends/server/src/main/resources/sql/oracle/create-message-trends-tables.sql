-- Oracle Migration script for Message Trends Management System

CREATE TABLE message_statistics_timeseries (
	id NUMBER(19,0) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	
	channel_id VARCHAR2(36) NOT NULL,
	connector_id VARCHAR2(36) NOT NULL, -- use '' for channel-level
	ts TIMESTAMP NOT NULL,
	bucket_size_minutes NUMBER(10,0) NOT NULL,
	
	received NUMBER(10,0) DEFAULT 0 NOT NULL,
	filtered NUMBER(10,0) DEFAULT 0 NOT NULL,
	queued NUMBER(10,0) DEFAULT 0 NOT NULL,
	sent NUMBER(10,0) DEFAULT 0 NOT NULL,
	error NUMBER(10,0) DEFAULT 0 NOT NULL,
	
	server_id VARCHAR2(36) NOT NULL
);

CREATE UNIQUE INDEX uq_mstats_key ON message_statistics_timeseries (server_id, channel_id, connector_id, ts, bucket_size_minutes);
CREATE INDEX idx_mstats_bucket_time ON message_statistics_timeseries (bucket_size_minutes, ts, channel_id, connector_id);
CREATE INDEX idx_mstats_server_time ON message_statistics_timeseries (server_id, ts, bucket_size_minutes);
CREATE INDEX idx_mstats_channel_time ON message_statistics_timeseries (channel_id, ts);
CREATE INDEX idx_mstats_connector_time ON message_statistics_timeseries (channel_id, connector_id, ts);