#!/bin/bash
# 1. Load the passwords from your .env file
# We use $(dirname "$0") to make sure it finds the .env even when run by cron
cd "$(dirname "$0")"
source .env
BACKUP_DIR="/srv/studyhelper/backups"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
mkdir -p "$BACKUP_DIR"
# 2. Backup Database (using the password from .env)
docker exec studyhelper-db-1 /usr/bin/mysqldump --no-tablespaces -u studyhelper --password="$DB_PASSWORD" studyhelper > "$BACKUP_DIR/db_backup_$TIMESTAMP.sql"
# 3. Backup Uploads (lives outside the repo since the security hardening pass)
tar -czf "$BACKUP_DIR/uploads_$TIMESTAMP.tar.gz" -C /srv/studyhelper uploads
# 4. Local Cleanup (Keep last 3 days on VPS)
find "$BACKUP_DIR" -type f -mtime +3 -delete
echo "Backup completed successfully at $TIMESTAMP"
