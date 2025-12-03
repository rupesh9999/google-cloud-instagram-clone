#!/bin/bash
# Disaster Recovery Script for Instagram Clone
# This script performs various DR operations

set -e

PROJECT_ID="instagram-clone-project1"
REGION="us-central1"
DR_REGION="us-east1"
SQL_INSTANCE="instagram-clone-prod-postgres"
GKE_CLUSTER="instagram-clone-prod-gke"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function: Create Cloud SQL read replica in DR region
create_sql_replica() {
    log_info "Creating Cloud SQL read replica in $DR_REGION..."
    gcloud sql instances create ${SQL_INSTANCE}-replica \
        --master-instance-name=$SQL_INSTANCE \
        --region=$DR_REGION \
        --tier=db-custom-2-8192 \
        --project=$PROJECT_ID
    log_info "Read replica created successfully"
}

# Function: Promote read replica to primary
promote_replica() {
    log_info "Promoting read replica to primary..."
    gcloud sql instances promote-replica ${SQL_INSTANCE}-replica \
        --project=$PROJECT_ID
    log_info "Replica promoted to primary"
}

# Function: Create on-demand backup
create_backup() {
    log_info "Creating on-demand backup..."
    gcloud sql backups create \
        --instance=$SQL_INSTANCE \
        --description="DR backup $(date +%Y%m%d-%H%M%S)" \
        --project=$PROJECT_ID
    log_info "Backup created successfully"
}

# Function: Restore from backup
restore_from_backup() {
    BACKUP_ID=$1
    if [ -z "$BACKUP_ID" ]; then
        log_error "Backup ID required"
        exit 1
    fi
    log_info "Restoring from backup $BACKUP_ID..."
    gcloud sql instances restore-backup $SQL_INSTANCE \
        --backup-id=$BACKUP_ID \
        --project=$PROJECT_ID
    log_info "Restore initiated"
}

# Function: Export database to GCS
export_database() {
    DB_NAME=$1
    BUCKET="gs://instagram-clone-prod-backups"
    TIMESTAMP=$(date +%Y%m%d-%H%M%S)
    
    log_info "Exporting $DB_NAME to GCS..."
    gcloud sql export sql $SQL_INSTANCE $BUCKET/${DB_NAME}-${TIMESTAMP}.sql \
        --database=$DB_NAME \
        --project=$PROJECT_ID
    log_info "Export completed: $BUCKET/${DB_NAME}-${TIMESTAMP}.sql"
}

# Function: Import database from GCS
import_database() {
    DB_NAME=$1
    SQL_FILE=$2
    
    log_info "Importing $SQL_FILE to $DB_NAME..."
    gcloud sql import sql $SQL_INSTANCE $SQL_FILE \
        --database=$DB_NAME \
        --project=$PROJECT_ID
    log_info "Import completed"
}

# Function: Scale GKE cluster for failover
scale_cluster() {
    NODE_COUNT=$1
    log_info "Scaling cluster to $NODE_COUNT nodes..."
    gcloud container clusters resize $GKE_CLUSTER \
        --num-nodes=$NODE_COUNT \
        --region=$REGION \
        --project=$PROJECT_ID \
        --quiet
    log_info "Cluster scaled to $NODE_COUNT nodes"
}

# Function: Check DR readiness
check_dr_readiness() {
    log_info "Checking DR readiness..."
    
    # Check Cloud SQL backups
    BACKUP_COUNT=$(gcloud sql backups list --instance=$SQL_INSTANCE --project=$PROJECT_ID --format="value(id)" | wc -l)
    if [ "$BACKUP_COUNT" -gt 0 ]; then
        log_info "✓ Cloud SQL backups available: $BACKUP_COUNT"
    else
        log_warn "✗ No Cloud SQL backups found"
    fi
    
    # Check GCS versioning
    VERSIONING=$(gsutil versioning get gs://instagram-clone-prod-media | grep -c "Enabled" || true)
    if [ "$VERSIONING" -gt 0 ]; then
        log_info "✓ GCS versioning enabled"
    else
        log_warn "✗ GCS versioning not enabled"
    fi
    
    # Check replica status
    REPLICA_STATUS=$(gcloud sql instances describe ${SQL_INSTANCE}-replica --project=$PROJECT_ID 2>/dev/null || echo "NOT_FOUND")
    if [ "$REPLICA_STATUS" != "NOT_FOUND" ]; then
        log_info "✓ Read replica exists"
    else
        log_warn "✗ No read replica configured"
    fi
    
    log_info "DR readiness check completed"
}

# Function: Full DR failover
failover() {
    log_warn "Initiating full DR failover..."
    log_warn "This will promote the replica and redirect traffic!"
    
    read -p "Are you sure you want to proceed? (yes/no): " CONFIRM
    if [ "$CONFIRM" != "yes" ]; then
        log_info "Failover cancelled"
        exit 0
    fi
    
    # Promote replica
    promote_replica
    
    # Update application configuration
    log_info "Update your application to point to the new database instance"
    log_info "New instance: ${SQL_INSTANCE}-replica"
    
    log_info "Failover completed. Manual DNS update may be required."
}

# Main menu
case "$1" in
    create-replica)
        create_sql_replica
        ;;
    promote-replica)
        promote_replica
        ;;
    backup)
        create_backup
        ;;
    restore)
        restore_from_backup $2
        ;;
    export)
        export_database $2
        ;;
    import)
        import_database $2 $3
        ;;
    scale)
        scale_cluster $2
        ;;
    check)
        check_dr_readiness
        ;;
    failover)
        failover
        ;;
    *)
        echo "Instagram Clone - Disaster Recovery Script"
        echo ""
        echo "Usage: $0 <command> [args]"
        echo ""
        echo "Commands:"
        echo "  create-replica     Create read replica in DR region"
        echo "  promote-replica    Promote replica to primary"
        echo "  backup             Create on-demand backup"
        echo "  restore <id>       Restore from backup ID"
        echo "  export <db>        Export database to GCS"
        echo "  import <db> <file> Import database from GCS"
        echo "  scale <count>      Scale GKE cluster"
        echo "  check              Check DR readiness"
        echo "  failover           Full DR failover"
        ;;
esac
