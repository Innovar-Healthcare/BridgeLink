-- SqlServer

-- 1) Add the column as NULL first
ALTER TABLE LOOKUP_GROUP
ADD VALUE_TYPE NVARCHAR(10) NULL;

-- 2) Backfill all existing rows with the desired default value
UPDATE LOOKUP_GROUP
SET VALUE_TYPE = 'TEXT'
WHERE VALUE_TYPE IS NULL;

-- 3a) Change the column to NOT NULL now that all existing rows have a value
ALTER TABLE LOOKUP_GROUP
ALTER COLUMN VALUE_TYPE NVARCHAR(10) NOT NULL;

-- 3b) Add an explicit named default constraint for future inserts
ALTER TABLE LOOKUP_GROUP
ADD CONSTRAINT DF_LOOKUP_GROUP_VALUE_TYPE DEFAULT 'TEXT' FOR VALUE_TYPE;


