# SUBFEEDER Data Migration - Usage Guide

## Overview

This guide explains how to migrate SUBFEEDER data from PostgreSQL to Smallworld GIS `master_subfeeder` collection.

**Source:** PostgreSQL table `dim_subfeeder_master_smallworld`
**Target:** Smallworld `master_subfeeder` collection
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

# Run SUBFEEDER migration
migrator.migrate_subfeeders()
```

### Step 3: Review Results
The migration will output:
- Total records processed
- Number of subfeeders created
- Number skipped (already exist)
- Number of errors

Example output:
```
=== SUBFEEDER Migration Started ===
Connecting to PostgreSQL...
Connected successfully
Executing query: SELECT * FROM smallworld.dim_subfeeder_master_smallworld ORDER BY subfeeder_code
Processing SUBFEEDER: SFD-001
  [OK] Created SUBFEEDER: SFD-001
Processing SUBFEEDER: SFD-002
  SKIPPED: SUBFEEDER already exists with subfeeder_code: SFD-002
...
  [COMMIT] Committed batch at record 1000
...
  [COMMIT] Final commit for 234 records
=== SUBFEEDER Migration Completed ===
Total records: 1234
Created: 1200
Skipped (already exists): 30
Errors: 4
```

---

## Field Mapping

PostgreSQL (dim_subfeeder_master_smallworld) → Smallworld (master_subfeeder)

| PostgreSQL Field | Smallworld Field | Type | Notes |
|-----------------|------------------|------|-------|
| subfeeder_id | id | Integer | Auto-generated or from source |
| subfeeder_id | subfeeder_id | Integer | Maps to both id and subfeeder_id |
| subfeeder_code | subfeeder_code | String | **Required** - Primary identifier |
| subfeeder_name | subfeeder_name | String | Display name |
| olt_code | olt_code | String | Reference to OLT |
| feeder_code | feeder_code | String | Reference to parent FEEDER |
| topology | topology | String | Network topology type |
| region | region | String | Geographic region |
| vendor_name | vendor_name | String | Vendor name |
| vendor_alias | vendor_alias | String | Vendor alias/code |
| subfeeder_length | subfeeder_length | Number | Length in meters |
| subfeeder_cable_type | subfeeder_cable_type | String | Cable type specification |
| N/A | subfeeder_capacity | Number | Set to _unset (not in PostgreSQL) |
| rfs_date | rfs_date | DateTime | Ready for service date |
| subfeeder_status | subfeeder_status | String | **Set to "NEW" for initial data** |
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
- `subfeeder_status` is **always set to "NEW"** regardless of PostgreSQL value to identify initial migration data

---

## Migration Behavior

### Duplicate Detection
The migrator checks for existing subfeeders by `subfeeder_code`:
- If `subfeeder_code` already exists → **SKIP** (no update)
- If `subfeeder_code` does not exist → **CREATE**

### Validation
Required fields:
- `subfeeder_code` must not be empty/NULL

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
Processing SUBFEEDER: SFD-001
  [OK] Created SUBFEEDER: SFD-001
...
Processing SUBFEEDER: SFD-1000
  [OK] Created SUBFEEDER: SFD-1000
  [COMMIT] Committed batch at record 1000
Processing SUBFEEDER: SFD-1001
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
subfeeder_col << ds.collections[:master_subfeeder]
write("Total master_subfeeder records: ", subfeeder_col.size)
```

**2. Check Specific SUBFEEDER**
```magik
ds << gis_program_manager.cached_dataset(:gis)
subfeeder_col << ds.collections[:master_subfeeder]

# Find by subfeeder_code
pred << predicate.eq(:subfeeder_code, "SFD-001")
subfeeder << subfeeder_col.select(pred).an_element()

_if subfeeder _isnt _unset
_then
	write("Found SUBFEEDER: ", subfeeder.subfeeder_code)
	write("Name: ", subfeeder.subfeeder_name)
	write("OLT Code: ", subfeeder.olt_code)
	write("Feeder Code: ", subfeeder.feeder_code)
	write("Region: ", subfeeder.region)
	write("Status: ", subfeeder.subfeeder_status)
	write("Cable Type: ", subfeeder.subfeeder_cable_type)
	write("Length: ", subfeeder.subfeeder_length)
_endif
```

**3. Verify Status is "NEW"**
```magik
ds << gis_program_manager.cached_dataset(:gis)
subfeeder_col << ds.collections[:master_subfeeder]

# Count subfeeders with status "NEW"
pred << predicate.eq(:subfeeder_status, "NEW")
new_subfeeders << subfeeder_col.select(pred)
write("Subfeeders with status NEW: ", new_subfeeders.size)
```

**4. Visual Verification**
Query and inspect subfeeder records in your GIS application.

---

## Re-running Migration

The migration is **idempotent** - you can run it multiple times safely:
- Existing subfeeders (by subfeeder_code) will be skipped
- Only new subfeeders will be created

**To Update Existing Subfeeders:**
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
**Error:** `WARNING: Skipping record - missing subfeeder_code`

**Solutions:**
1. Run validation query in PostgreSQL:
```sql
SELECT COUNT(*) FROM dim_subfeeder_master_smallworld
WHERE subfeeder_code IS NULL OR subfeeder_code = '';
```
2. Fix NULL/empty subfeeder_code values

### Duplicate Records
**Message:** `SKIPPED: SUBFEEDER already exists with subfeeder_code: XXX`

This is normal behavior if:
- Migration was run previously
- Records already exist in master_subfeeder

If unexpected, check:
1. Which records exist: `SELECT subfeeder_code FROM master_subfeeder`
2. PostgreSQL source data for duplicates

### Parent Records Missing
If you see subfeeders without corresponding parent records:
1. Verify FEEDER migration was run first
2. Check that `feeder_code` values match between tables
3. Verify OLT records exist in `mit_hub` collection

---

## Migration Phases

This is part of a multi-phase migration plan:

- **Phase 1:** OLT data migration (COMPLETED)
- **Phase 2:** FEEDER data migration (COMPLETED)
- **Phase 3:** SUBFEEDER data migration (THIS GUIDE)
- **Phase 4:** Cluster design data migration (FUTURE)

**Recommended Migration Order:**
1. OLT migration first (parent records)
2. FEEDER migration second (references OLT)
3. SUBFEEDER migration third (references FEEDER and OLT)

---

## Support

For issues during migration:
1. Review error messages in Magik console
2. Check PostgreSQL connection and data
3. Verify Smallworld database permissions
4. Review migration statistics for patterns
5. Verify parent records (OLT and FEEDER) exist

---

**Created:** 2026-01-29
**Version:** 1.0
**Module:** rwwi_astri_integration
