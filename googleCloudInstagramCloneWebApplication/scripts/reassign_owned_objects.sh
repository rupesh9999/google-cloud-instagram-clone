#!/usr/bin/env bash
set -euo pipefail

# Reassign objects owned by a DB role to a target owner across multiple databases
# Usage: ./reassign_owned_objects.sh <PROJECT_ID> <INSTANCE_NAME> <ROLE_TO_CHANGE> <NEW_OWNER> <db1,db2,...>

if [ "$#" -lt 5 ]; then
  echo "Usage: $0 <PROJECT_ID> <INSTANCE_NAME> <ROLE_TO_CHANGE> <NEW_OWNER> <db1,db2,...>"
  exit 1
fi

PROJECT_ID="$1"
INSTANCE_NAME="$2"
OLD_ROLE="$3"
NEW_OWNER="$4"
DB_LIST="$5"

IFS=',' read -ra DBS <<< "$DB_LIST"

for DB in "${DBS[@]}"; do
  echo "Processing database: $DB"
  # Use gcloud to connect to the database and run REASSIGN OWNED
  # This requires psql client to be available on the machine where this script runs.
  # If you run this from Cloud Shell or from a machine in the VPC, it should work.
  gcloud sql connect "$INSTANCE_NAME" --project="$PROJECT_ID" --user=postgres --quiet <<PSQL
\c $DB
REASSIGN OWNED BY $OLD_ROLE TO $NEW_OWNER;
-- Optional: If you want to drop objects owned by the old role, uncomment this line
-- DROP OWNED BY $OLD_ROLE;
\q
PSQL
done

echo "Reassign finished for DBs: $DB_LIST"
