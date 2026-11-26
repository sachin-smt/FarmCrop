-- Creates the FarmCrop database ONLY if it does not already exist. Never drops or alters anything.
-- Tables are created afterwards by Flyway when the backend starts (backend/src/main/resources/db/migration).
--
-- Run from the repository root in PowerShell (you are prompted for the password; it is never shown):
--   & "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p --table -e "source backend/scripts/create-database.sql"

CREATE DATABASE IF NOT EXISTS farmcrop
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

SELECT SCHEMA_NAME AS database_name,
       DEFAULT_CHARACTER_SET_NAME AS charset,
       DEFAULT_COLLATION_NAME AS collation_name
FROM information_schema.SCHEMATA
WHERE SCHEMA_NAME = 'farmcrop';
