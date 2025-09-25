-- Oracle Migration script for Message Trends Management System

CREATE TABLE message_statistics_timeseries (
	id NUMBER(19,0) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	
	channel_id VARCHAR2(36) NOT NULL,
	connector_id VARCHAR2(36) NOT NULL, -- use '__EMPTY__' for channel-level
	ts TIMESTAMP NOT NULL,
	bucket_size_minutes NUMBER(10,0) NOT NULL,
	
	received NUMBER(10,0) DEFAULT 0 NOT NULL,
	filtered NUMBER(10,0) DEFAULT 0 NOT NULL,
	queued NUMBER(10,0) DEFAULT 0 NOT NULL,
	sent NUMBER(10,0) DEFAULT 0 NOT NULL,
	error NUMBER(10,0) DEFAULT 0 NOT NULL,
	
	server_id VARCHAR2(36) NOT NULL,
	
	CONSTRAINT uq_mstats_key UNIQUE (server_id, channel_id, connector_id, bucket_size_minutes, ts)
);

CREATE INDEX idx_mstats_server_bucket_ts ON message_statistics_timeseries (server_id, bucket_size_minutes, ts);