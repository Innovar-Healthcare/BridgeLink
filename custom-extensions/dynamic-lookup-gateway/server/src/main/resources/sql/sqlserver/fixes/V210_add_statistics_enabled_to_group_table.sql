-- SqlServer

-- 1) Add the column as NULL first
ALTER TABLE LOOKUP_GROUP
ADD STATISTICS_ENABLED BIT NULL;

-- 2) Backfill all existing rows with the desired default value
UPDATE LOOKUP_GROUP
SET STATISTICS_ENABLED = 1
WHERE STATISTICS_ENABLED IS NULL;

-- 3) Change the column to NOT NULL now that all existing rows have a value
ALTER TABLE LOOKUP_GROUP
ALTER COLUMN STATISTICS_ENABLED BIT NOT NULL;