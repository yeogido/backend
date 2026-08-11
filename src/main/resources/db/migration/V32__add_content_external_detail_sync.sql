ALTER TABLE content
    MODIFY COLUMN description TEXT NULL,
    ADD COLUMN external_details_synced_at DATETIME(6) NULL;
