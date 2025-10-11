------------------------------------------------------------
-- LOOKUP_GROUP
------------------------------------------------------------
DECLARE @sql NVARCHAR(MAX);

-- drop default constraint if exists
SELECT @sql = N'ALTER TABLE LOOKUP_GROUP DROP CONSTRAINT [' + dc.name + N']'
FROM sys.default_constraints dc
JOIN sys.columns c
  ON dc.parent_object_id = c.object_id
 AND dc.parent_column_id = c.column_id
WHERE OBJECT_NAME(dc.parent_object_id) = 'LOOKUP_GROUP'
  AND c.name = 'CACHE_POLICY';

IF @sql IS NOT NULL
    EXEC sp_executesql @sql;

-- drop index if exists
IF EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'IDX_LOOKUP_GROUP_NAME'
      AND object_id = OBJECT_ID('LOOKUP_GROUP')
)
    DROP INDEX IDX_LOOKUP_GROUP_NAME ON LOOKUP_GROUP;

-- alter columns
ALTER TABLE LOOKUP_GROUP ALTER COLUMN NAME NVARCHAR(255) NOT NULL;
ALTER TABLE LOOKUP_GROUP ALTER COLUMN DESCRIPTION NVARCHAR(MAX);
ALTER TABLE LOOKUP_GROUP ALTER COLUMN VERSION NVARCHAR(50);
ALTER TABLE LOOKUP_GROUP ALTER COLUMN CACHE_POLICY NVARCHAR(50);

-- add default constraint explicitly named
ALTER TABLE LOOKUP_GROUP
    ADD CONSTRAINT DF_LOOKUP_GROUP_CACHE_POLICY DEFAULT 'LRU' FOR CACHE_POLICY;

-- recreate index
CREATE UNIQUE INDEX IDX_LOOKUP_GROUP_NAME ON LOOKUP_GROUP (NAME);
GO

------------------------------------------------------------
-- LOOKUP_AUDIT
------------------------------------------------------------
-- drop index if exists
IF EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'IDX_LOOKUP_AUDIT_KEY'
      AND object_id = OBJECT_ID('LOOKUP_AUDIT')
)
    DROP INDEX IDX_LOOKUP_AUDIT_KEY ON LOOKUP_AUDIT;

-- alter columns
ALTER TABLE LOOKUP_AUDIT ALTER COLUMN TABLE_NAME NVARCHAR(255) NOT NULL;
ALTER TABLE LOOKUP_AUDIT ALTER COLUMN KEY_VALUE NVARCHAR(255) NOT NULL;
ALTER TABLE LOOKUP_AUDIT ALTER COLUMN [ACTION] NVARCHAR(50) NOT NULL;
ALTER TABLE LOOKUP_AUDIT ALTER COLUMN OLD_VALUE NVARCHAR(MAX);
ALTER TABLE LOOKUP_AUDIT ALTER COLUMN NEW_VALUE NVARCHAR(MAX);
ALTER TABLE LOOKUP_AUDIT ALTER COLUMN USER_ID NVARCHAR(255);

-- recreate index
CREATE INDEX IDX_LOOKUP_AUDIT_KEY ON LOOKUP_AUDIT (TABLE_NAME, KEY_VALUE);
GO

------------------------------------------------------------
-- LOOKUP_VALUE_%
------------------------------------------------------------
DECLARE @sql NVARCHAR(MAX);
DECLARE @tableList NVARCHAR(MAX) = '';

-- Build list of LOOKUP_VALUE_% tables
SELECT @tableList = STRING_AGG(t.name, ',')
FROM sys.tables t
WHERE t.name LIKE 'LOOKUP_VALUE_%';

-- Process each table individually using dynamic SQL
IF @tableList IS NOT NULL
BEGIN
    -- Split the table list and process each table
    DECLARE @pos INT = 1;
    DECLARE @len INT = LEN(@tableList);
    DECLARE @tableName NVARCHAR(255);
    DECLARE @nextComma INT;
    
    WHILE @pos <= @len
    BEGIN
        SET @nextComma = CHARINDEX(',', @tableList, @pos);
        IF @nextComma = 0 SET @nextComma = @len + 1;
        
        SET @tableName = LTRIM(RTRIM(SUBSTRING(@tableList, @pos, @nextComma - @pos)));
        
        IF LEN(@tableName) > 0
        BEGIN
            -- Drop primary key constraint if exists
            SET @sql = '
            DECLARE @pkName NVARCHAR(255);
            SELECT @pkName = kc.name
            FROM sys.key_constraints kc
            JOIN sys.tables t ON kc.parent_object_id = t.object_id
            WHERE t.name = ''' + @tableName + ''' AND kc.type = ''PK'';
            
            IF @pkName IS NOT NULL
            BEGIN
                DECLARE @dropSql NVARCHAR(MAX) = ''ALTER TABLE [' + @tableName + '] DROP CONSTRAINT ['' + @pkName + '']'';
                EXEC sp_executesql @dropSql;
            END';
            EXEC sp_executesql @sql;
            
            -- Alter KEY_VALUE column
            SET @sql = 'ALTER TABLE [' + @tableName + '] ALTER COLUMN KEY_VALUE NVARCHAR(255) NOT NULL';
            EXEC sp_executesql @sql;
            
            -- Alter VALUE_DATA column
            SET @sql = 'ALTER TABLE [' + @tableName + '] ALTER COLUMN VALUE_DATA NVARCHAR(MAX)';
            EXEC sp_executesql @sql;
            
            -- Re-add primary key constraint
            SET @sql = '
            IF NOT EXISTS (
                SELECT 1 FROM sys.key_constraints kc
                JOIN sys.tables t ON kc.parent_object_id = t.object_id
                WHERE t.name = ''' + @tableName + ''' AND kc.type = ''PK''
            )
            BEGIN
                DECLARE @newPkName NVARCHAR(255) = ''PK_' + @tableName + ''';
                DECLARE @addSql NVARCHAR(MAX) = ''ALTER TABLE [' + @tableName + '] ADD CONSTRAINT ['' + @newPkName + ''] PRIMARY KEY (KEY_VALUE)'';
                EXEC sp_executesql @addSql;
            END';
            EXEC sp_executesql @sql;
        END
        
        SET @pos = @nextComma + 1;
    END
END
GO