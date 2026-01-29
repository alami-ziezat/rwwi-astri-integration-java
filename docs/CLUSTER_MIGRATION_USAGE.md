# CLUSTER Data Migration - Usage Guide

## Overview

This guide explains how to migrate CLUSTER data from PostgreSQL to Smallworld GIS `master_cluster` collection.

**Source:** PostgreSQL table `dim_cluster_master_smallworld`
**Target:** Smallworld `master_cluster` collection
**Implementation:** `astri_data_migrator.magik`

---

## Prerequisites

### 1. PostgreSQL Connection
Ensure PostgreSQL connection is configured in:
```
rwi_external_databases/resources/base/data/connection_external_ds.cfg
```

Current configuration:
```
[POSTGRESQL_ASTRI_DB]
connect_name=ASTRI_DB
driver=org.postgresql.Driver
data_source=DATA_SOURCE_ASTRI
classpath=POSTGRESQL_JDBC_PATH
databasename=postgres
dbusername=iotech_data
dbpassword=Hmjl2MV8d!
```

### 2. Environment Variables
Set these environment variables before starting Smallworld:
- `DATA_SOURCE_ASTRI` - PostgreSQL JDBC URL (e.g., `jdbc:postgresql://172.17.52.32:5432/postgres`)
- `POSTGRESQL_JDBC_PATH` - Path to PostgreSQL JDBC driver JAR

### 3. Database Backup
**IMPORTANT:** Backup your Smallworld database before migration!

---

## Running the Migration

### Step 1: Start Smallworld
Launch Smallworld with your GIS dataset loaded.

### Step 2: Execute Migration
At the Magik prompt or in Emacs, execute:

```magik
# Get the database
ds << gis_program_manager.cached_dataset(:gis)

# Create migrator instance
migrator << astri_data_migrator.new(ds)

# Run CLUSTER migration
migrator.migrate_clusters()
```

### Step 3: Review Results
The migration will output:
- Total records processed
- Number of clusters created
- Number skipped (already exist)
- Number of errors

Example output:
```
=== CLUSTER Migration Started ===
Connecting to PostgreSQL...
Connected successfully
Executing query: SELECT * FROM smallworld.dim_cluster_master_smallworld ORDER BY cluster_code
Processing CLUSTER: CLU-001
  [OK] Created CLUSTER: CLU-001
Processing CLUSTER: CLU-002
  SKIPPED: CLUSTER already exists with cluster_code: CLU-002
...
  [COMMIT] Committed batch at record 1000
...
  [COMMIT] Final commit for 234 records
=== CLUSTER Migration Completed ===
Total records: 1234
Created: 1200
Skipped (already exists): 30
Errors: 4
```

---

## Field Mapping

PostgreSQL (dim_cluster_master_smallworld) → Smallworld (master_cluster)

| PostgreSQL Field | Smallworld Field | Type | Notes |
|-----------------|------------------|------|-------|
| cluster_id | id | Integer | Auto-generated or from source |
| cluster_id | cluster_id | Integer | Maps to both id and cluster_id |
| cluster_code | cluster_code | String | **Required** - Primary identifier |
| cluster_name | cluster_name | String | Display name |
| subfeeder_code | subfeeder_code | String | Reference to parent SUBFEEDER |
| feeder_code | feeder_code | String | Reference to FEEDER |
| olt_code | olt_code | String | Reference to OLT |
| topology | topology | String | Network topology type |
| region | region | String | Geographic region |
| vendor_name | vendor_name | String | Vendor name |
| vendor_alias | vendor_alias | String | Vendor alias/code |
| cluster_hp | cluster_hp | String | Cluster home pass |
| N/A | subfeeder_capacity | Number | Set to _unset (not in PostgreSQL) |
| N/A | subfeeder_length | Number | Set to _unset (not in PostgreSQL) |
| rfs_date | rfs_date | DateTime | Ready for service date |
| cluster_status | subfeeder_status | String | **Set to "NEW" for initial data** |
| abd_kmz_uuid | abd_kmz_uuid | String | As-Built Design KMZ UUID |
| apd_kmz_uuid | apd_kmz_uuid | String | As-Plan Design KMZ UUID |
| abd_kmz_verified_at | abd_kmz_verified_at | DateTime | ABD KMZ verification timestamp |
| apd_kmz_verified_at | apd_kmz_verified_at | DateTime | APD KMZ verification timestamp |
| created_timestamp | created_timestamp | DateTime | Record creation time |
| updated_timestamp | updated_timestamp | DateTime | Last update time |
| synced_timestamp | synced_timestamp | DateTime | Last sync time |

**PostgreSQL Fields NOT Mapped:**
- `olt_code_boss` - Not present in Smallworld schema

**Smallworld-only Fields (not mapped):**
- `ds!version` - Version control (managed by Smallworld)
- `int!info_flags` - Internal flags (managed by Smallworld)

**Special Handling:**
- `cluster_status` from PostgreSQL → `subfeeder_status` in Smallworld (always set to "NEW")
- This identifies records as initial migration data
- Note: The master_cluster table reuses subfeeder-related field names for cluster data

---

## Migration Behavior

### Duplicate Detection
The migrator checks for existing clusters by `cluster_code`:
- If `cluster_code` already exists → **SKIP** (no update)
- If `cluster_code` does not exist → **CREATE**

### Validation
Required fields:
- `cluster_code` must not be empty/NULL

If validation fails → record is skipped and counted as error

### Batch Commit Strategy
For better performance and data safety:
- **Commit Interval:** Every 1000 records
- **Progress Messages:** Shows commit points during migration
- **Final Commit:** Commits remaining records after loop completes
- **Benefits:**
  - Prevents memory issues with large datasets
  - Allows partial recovery if migration fails mid-way
  - Better transaction log management

Example output:
```
Processing CLUSTER: CLU-001
  [OK] Created CLUSTER: CLU-001
...
Processing CLUSTER: CLU-1000
  [OK] Created CLUSTER: CLU-1000
  [COMMIT] Committed batch at record 1000
Processing CLUSTER: CLU-1001
...
  [COMMIT] Final commit for 234 records
```

### Error Handling
The migrator continues processing even if individual records fail:
- Connection errors → migration stops
- Record validation errors → record skipped, migration continues
- Batch commit errors → rollback and continue (logs warning)

---

## Verification

### After Migration

**1. Count Records**
```magik
ds << gis_program_manager.cached_dataset(:gis)
cluster_col << ds.collections[:master_cluster]
write("Total master_cluster records: ", cluster_col.size)
```

**2. Check Specific CLUSTER**
```magik
ds << gis_program_manager.cached_dataset(:gis)
cluster_col << ds.collections[:master_cluster]

# Find by cluster_code
pred << predicate.eq(:cluster_code, "CLU-001")
cluster << cluster_col.select(pred).an_element()

_if cluster _isnt _unset
_then
	write("Found CLUSTER: ", cluster.cluster_code)
	write("Name: ", cluster.cluster_name)
	write("Subfeeder Code: ", cluster.subfeeder_code)
	write("Feeder Code: ", cluster.feeder_code)
	write("OLT Code: ", cluster.olt_code)
	write("Region: ", cluster.region)
	write("Status: ", cluster.subfeeder_status)
	write("Cluster HP: ", cluster.cluster_hp)
_endif
```

**3. Verify Status is "NEW"**
```magik
ds << gis_program_manager.cached_dataset(:gis)
cluster_col << ds.collections[:master_cluster]

# Count clusters with status "NEW"
pred << predicate.eq(:subfeeder_status, "NEW")
new_clusters << cluster_col.select(pred)
write("Clusters with status 'NEW': ", new_clusters.size)
```

**4. Visual Verification**
Query and inspect cluster records in your GIS application.

---

## Re-running Migration

The migration is **idempotent** - you can run it multiple times safely:
- Existing clusters (by cluster_code) will be skipped
- Only new clusters will be created

**To Update Existing Clusters:**
You must manually delete them first or create a separate update script.

---

## Troubleshooting

### Connection Failed
**Error:** `ERROR: Failed to connect to PostgreSQL`

**Solutions:**
1. Check environment variables are set correctly
2. Verify PostgreSQL is running: `ping 172.17.52.32`
3. Test connection credentials
4. Verify JDBC driver path

### Missing Fields
**Error:** `WARNING: Skipping record - missing cluster_code`

**Solutions:**
1. Run validation query in PostgreSQL:
```sql
SELECT COUNT(*) FROM dim_cluster_master_smallworld
WHERE cluster_code IS NULL OR cluster_code = '';
```
2. Fix NULL/empty cluster_code values

### Duplicate Records
**Message:** `SKIPPED: CLUSTER already exists with cluster_code: XXX`

This is normal behavior if:
- Migration was run previously
- Records already exist in master_cluster

If unexpected, check:
1. Which records exist: `SELECT cluster_code FROM master_cluster`
2. PostgreSQL source data for duplicates

### Parent Records Missing
If you see clusters without corresponding parent records:
1. Verify SUBFEEDER migration was run first
2. Check that `subfeeder_code` values match between tables
3. Verify FEEDER records exist in `master_feeder` collection
4. Verify OLT records exist in `mit_hub` collection

---

## Migration Phases

This is part of a multi-phase migration plan:

- **Phase 1:** OLT data migration (COMPLETED)
- **Phase 2:** FEEDER data migration (COMPLETED)
- **Phase 3:** SUBFEEDER data migration (COMPLETED)
- **Phase 4:** CLUSTER data migration (THIS GUIDE)

**Recommended Migration Order:**
1. OLT migration first (parent records)
2. FEEDER migration second (references OLT)
3. SUBFEEDER migration third (references FEEDER and OLT)
4. CLUSTER migration fourth (references SUBFEEDER, FEEDER, and OLT)

---

## Support

For issues during migration:
1. Review error messages in Magik console
2. Check PostgreSQL connection and data
3. Verify Smallworld database permissions
4. Review migration statistics for patterns
5. Verify parent records (OLT, FEEDER, and SUBFEEDER) exist

---

**Created:** 2026-01-29
**Version:** 1.0
**Module:** rwwi_astri_integration
