# FEEDER Data Migration - Usage Guide

## Overview

This guide explains how to migrate FEEDER data from PostgreSQL to Smallworld GIS `master_feeder` collection.

**Source:** PostgreSQL table `dim_feeder_master_smallworld`
**Target:** Smallworld `master_feeder` collection
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

# Run FEEDER migration
migrator.migrate_feeders()
```

### Step 3: Review Results
The migration will output:
- Total records processed
- Number of feeders created
- Number skipped (already exist)
- Number of errors

Example output:
```
=== FEEDER Migration Started ===
Connecting to PostgreSQL...
Connected successfully
Executing query: SELECT * FROM smallworld.dim_feeder_master_smallworld ORDER BY feeder_code
Processing FEEDER: FDR-001
  [OK] Created FEEDER: FDR-001
Processing FEEDER: FDR-002
  SKIPPED: FEEDER already exists with feeder_code: FDR-002
...
  [COMMIT] Committed batch at record 1000
...
  [COMMIT] Final commit for 234 records
=== FEEDER Migration Completed ===
Total records: 1234
Created: 1200
Skipped (already exists): 30
Errors: 4
```

---

## Field Mapping

PostgreSQL (dim_feeder_master_smallworld) → Smallworld (master_feeder)

| PostgreSQL Field | Smallworld Field | Type | Notes |
|-----------------|------------------|------|-------|
| feeder_id | id | Integer | Auto-generated or from source |
| feeder_code | feeder_code | String | **Required** - Primary identifier |
| feeder_name | feeder_name | String | Display name |
| olt_code | olt_code | String | Reference to OLT |
| topology | topology | String | Network topology type |
| region | region | String | Geographic region |
| vendor_name | vendor_name | String | Vendor name |
| vendor_alias | vendor_alias | String | Vendor alias/code |
| feeder_length | feeder_length | Number | Length in meters |
| feeder_capacity | feeder_capacity | Number | Capacity count |
| rfs_date | rfs_date | DateTime | Ready for service date |
| feeder_status | feeder_status | String | **Set to "NEW" for initial data** |
| abd_kmz_uuid | abd_kmz_uuid | String | As-Built Design KMZ UUID |
| apd_kmz_uuid | apd_kmz_uuid | String | As-Plan Design KMZ UUID |
| abd_kmz_verified_at | abd_kmz_verified_at | DateTime | ABD KMZ verification timestamp |
| apd_kmz_verified_at | apd_kmz_verified_at | DateTime | APD KMZ verification timestamp |
| created_timestamp | created_timestamp | DateTime | Record creation time |
| updated_timestamp | updated_timestamp | DateTime | Last update time |
| synced_timestamp | synced_timestamp | DateTime | Last sync time |

**Smallworld-only Fields (not mapped):**
- `ds!version` - Version control (managed by Smallworld)
- `int!info_flags` - Internal flags (managed by Smallworld)

**Special Handling:**
- `feeder_status` is **always set to "NEW"** regardless of PostgreSQL value to identify initial migration data

---

## Migration Behavior

### Duplicate Detection
The migrator checks for existing feeders by `feeder_code`:
- If `feeder_code` already exists → **SKIP** (no update)
- If `feeder_code` does not exist → **CREATE**

### Validation
Required fields:
- `feeder_code` must not be empty/NULL

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
Processing FEEDER: FDR-001
  [OK] Created FEEDER: FDR-001
...
Processing FEEDER: FDR-1000
  [OK] Created FEEDER: FDR-1000
  [COMMIT] Committed batch at record 1000
Processing FEEDER: FDR-1001
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
feeder_col << ds.collections[:master_feeder]
write("Total master_feeder records: ", feeder_col.size)
```

**2. Check Specific FEEDER**
```magik
ds << gis_program_manager.cached_dataset(:gis)
feeder_col << ds.collections[:master_feeder]

# Find by feeder_code
pred << predicate.eq(:feeder_code, "FDR-001")
feeder << feeder_col.select(pred).an_element()

_if feeder _isnt _unset
_then
	write("Found FEEDER: ", feeder.feeder_code)
	write("Name: ", feeder.feeder_name)
	write("OLT Code: ", feeder.olt_code)
	write("Region: ", feeder.region)
	write("Status: ", feeder.feeder_status)
	write("Capacity: ", feeder.feeder_capacity)
_endif
```

**3. Verify Status is "NEW"**
```magik
ds << gis_program_manager.cached_dataset(:gis)
feeder_col << ds.collections[:master_feeder]

# Count feeders with status "NEW"
pred << predicate.eq(:feeder_status, "NEW")
new_feeders << feeder_col.select(pred)
write("Feeders with status 'NEW': ", new_feeders.size)
```

**4. Visual Verification**
Query and inspect feeder records in your GIS application.

---

## Re-running Migration

The migration is **idempotent** - you can run it multiple times safely:
- Existing feeders (by feeder_code) will be skipped
- Only new feeders will be created

**To Update Existing Feeders:**
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
**Error:** `WARNING: Skipping record - missing feeder_code`

**Solutions:**
1. Run validation query in PostgreSQL:
```sql
SELECT COUNT(*) FROM dim_feeder_master_smallworld
WHERE feeder_code IS NULL OR feeder_code = '';
```
2. Fix NULL/empty feeder_code values

### Duplicate Records
**Message:** `SKIPPED: FEEDER already exists with feeder_code: XXX`

This is normal behavior if:
- Migration was run previously
- Records already exist in master_feeder

If unexpected, check:
1. Which records exist: `SELECT feeder_code FROM master_feeder`
2. PostgreSQL source data for duplicates

---

## Migration Phases

This is part of a multi-phase migration plan:

- **Phase 1:** OLT data migration (COMPLETED)
- **Phase 2:** FEEDER data migration (THIS GUIDE)
- **Phase 3:** Subfeeder design data migration (FUTURE)
- **Phase 4:** Cluster design data migration (FUTURE)

---

## Support

For issues during migration:
1. Review error messages in Magik console
2. Check PostgreSQL connection and data
3. Verify Smallworld database permissions
4. Review migration statistics for patterns

---

**Created:** 2026-01-27
**Version:** 1.0
**Module:** rwwi_astri_integration
