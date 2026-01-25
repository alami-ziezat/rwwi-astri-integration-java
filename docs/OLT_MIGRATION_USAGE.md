# OLT Data Migration - Simple Usage Guide

## Overview

This guide explains how to migrate OLT (Optical Line Terminal) data from PostgreSQL to Smallworld GIS `mit_hub` collection.

**Source:** PostgreSQL table `dim_olt_master_smallworld`
**Target:** Smallworld `mit_hub` collection
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

# Run migration
migrator.migrate_olts()
```

### Step 3: Review Results
The migration will output:
- Total records processed
- Number of OLTs created
- Number skipped (already exist)
- Number of errors

Example output:
```
=== OLT Migration Started ===
Connecting to PostgreSQL...
Connected successfully
Executing query: SELECT * FROM dim_olt_master_smallworld ORDER BY olt_code
Processing OLT: OLT-001
  [OK] Created OLT: OLT-001
Processing OLT: OLT-002
  SKIPPED: OLT already exists with olt_code: OLT-002
...
=== Migration Completed ===
Total records: 150
Created: 145
Skipped (already exists): 3
Errors: 2
```

---

## Field Mapping

PostgreSQL (dim_olt_master_smallworld) → Smallworld (mit_hub)

| PostgreSQL Field | Smallworld Field | Type | Notes |
|-----------------|------------------|------|-------|
| olt_code | olt_code | String | **Required** - Primary identifier |
| olt_label | olt | String | OLT label field |
| latitude | location | Coordinate | **Required** - Transformed to local coord |
| longitude | location | Coordinate | **Required** - Transformed to local coord |
| olt_name | name | String | Display name (or olt_label if empty) |
| device_id | object_id | String | Device/Object identifier |
| owner | asset_owner | String | **Conditional**: "MYREPUBLIC" → "Owned", else "Third Party" |
| olt_area | region | String | OLT area/region |
| bng_area | sub_region | String | BNG area |
| bng_code | segment | String | BNG code |
| olt_hostname | span | String | OLT hostname |
| (all other fields) | notes | Text | **Pipe-delimited**: IP, NMS IP, Brand, Type, BOSS Code, Category, Site, Version, Backup Uplink, TACACS, dates |
| (constant "OLT") | type | String | Set to "OLT" |
| (constant "In Service") | construction_status | String | Set to "In Service" |
| (constant "Primary") | fttx_network_type | String | Set to "Primary" |
| (empty "") | annotation_2 | String | Not mapped (empty string) |
| _unset | floor_plan | - | Not mapped |
| _unset | mit_structure_point | - | Not mapped |
| _unset | folders | - | Not mapped |
| _unset | project | - | Not mapped |
| _unset | stf_item_code | - | Not mapped |
| _unset | cluster | - | Not mapped |
| _unset | ring_name | - | Not mapped |
| _unset | jalur | - | Not mapped |
| _unset | line_type | - | Not mapped |
| _unset | uuid | - | Not mapped |

**PostgreSQL Fields Included in Notes (Pipe-delimited):**
- ip_address → "IP: xxx.xxx.xxx.xxx"
- nms_ip_address → "NMS IP: xxx.xxx.xxx.xxx"
- olt_brand → "Brand: Huawei"
- type → "Type: MA5800"
- olt_boss_code → "BOSS Code: OLT001"
- category → "Category: Primary"
- site_code → "Site: SITE001"
- system_device_version → "Version: V800R017C10"
- is_backup_uplink_available → "Backup Uplink: Yes/No"
- olt_tacacs_configured → "TACACS: Yes/No"
- rfs_date → "RFS: 2024-01-15"
- nms_integration_date → "NMS Integration: 2024-01-20"
- target_rfs_period → "Target RFS: 2024-01-31"

**Example notes field:**
```
IP: 172.17.10.5 | NMS IP: 172.17.52.32 | Brand: Huawei | Type: MA5800 | BOSS Code: OLT001 | Category: Primary | Site: SITE001 | Version: V800R017C10 | Backup Uplink: Yes | TACACS: No | RFS: 2024-01-15 | NMS Integration: 2024-01-20
```

**PostgreSQL Fields NOT Migrated:**
- olt_id (auto-generated ID)
- created_timestamp, updated_timestamp (audit timestamps - not needed in Smallworld)

---

## Migration Behavior

### Duplicate Detection
The migrator checks for existing OLTs by `olt_code`:
- If `olt_code` already exists → **SKIP** (no update)
- If `olt_code` does not exist → **CREATE**

### Validation
Required fields:
- `olt_code` must not be empty/NULL
- `latitude` must not be NULL
- `longitude` must not be NULL
- Coordinates must not be (0.0, 0.0)
- Coordinates must be valid numbers

If validation fails → record is skipped:
- NULL/missing coordinates → counted as skipped
- Invalid coordinates (0.0, 0.0) → counted as skipped
- Missing olt_code → counted as error

### Coordinate Transformation
- Input: WGS84 latitude/longitude (decimal degrees)
- Output: Local coordinate system (Smallworld world)
- Transformation: Uses project's coordinate system definition

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
Processing OLT: OLT-001
  [OK] Created OLT: OLT-001
...
Processing OLT: OLT-1000
  [OK] Created OLT: OLT-1000
  [COMMIT] Committed batch at record 1000
Processing OLT: OLT-1001
...
  [COMMIT] Final commit for 150 records
```

### Error Handling
The migrator continues processing even if individual records fail:
- Connection errors → migration stops
- Record validation errors → record skipped, migration continues
- Coordinate transformation errors → record skipped, migration continues
- Batch commit errors → rollback and continue (logs warning)

---

## Verification

### After Migration

**1. Count Records**
```magik
ds << gis_program_manager.cached_dataset(:gis)
mit_hub_col << ds.collections[:mit_hub]
write("Total mit_hub objects: ", mit_hub_col.size)
```

**2. Check Specific OLT**
```magik
ds << gis_program_manager.cached_dataset(:gis)
mit_hub_col << ds.collections[:mit_hub]

# Find by olt_code
pred << predicate.eq(:olt_code, "OLT-001")
olt << mit_hub_col.select(pred).an_element()

_if olt _isnt _unset
_then
	write("Found OLT: ", olt.olt_code)
	write("Name: ", olt.name)
	write("Location: ", olt.location)
	write("IP Address: ", olt.ip_address)
_endif
```

**3. Visual Verification**
Open the map view and zoom to OLT locations to verify geometry placement.

---

## Re-running Migration

The migration is **idempotent** - you can run it multiple times safely:
- Existing OLTs (by olt_code) will be skipped
- Only new OLTs will be created

**To Update Existing OLTs:**
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

### NULL or Missing Coordinates
**Message:** `SKIPPED: NULL or missing coordinates for OLT-XXX`

**Solutions:**
1. Check for NULL latitude/longitude in PostgreSQL:
```sql
SELECT COUNT(*) FROM dim_olt_master_smallworld
WHERE latitude IS NULL OR longitude IS NULL;
```
2. Fix NULL values in source database before migration
3. These records will be skipped (not migrated)

### Invalid Coordinates
**Message:** `SKIPPED: Invalid coordinates (0.0, 0.0) for OLT-XXX`
**Error:** `ERROR: Failed to transform coordinates for OLT-XXX`

**Solutions:**
1. Check latitude/longitude values in PostgreSQL
2. Valid ranges: latitude (-90 to 90), longitude (-180 to 180)
3. Coordinates (0.0, 0.0) are considered invalid
4. Fix invalid data in source database

### Missing Fields
**Error:** `WARNING: Skipping record - missing olt_code`

**Solutions:**
1. Run validation query in PostgreSQL:
```sql
SELECT COUNT(*) FROM dim_olt_master_smallworld
WHERE olt_code IS NULL OR olt_code = '';
```
2. Fix NULL/empty olt_code values

---

## Next Phases

After successful OLT migration:
- **Phase 2:** Feeder design data migration
- **Phase 3:** Subfeeder design data migration
- **Phase 4:** Cluster design data migration

Each phase will reference OLTs created in Phase 1.

---

## Support

For issues during migration:
1. Review error messages in Magik console
2. Check PostgreSQL connection and data
3. Verify Smallworld database permissions
4. Review migration statistics for patterns

---

**Created:** 2026-01-25
**Version:** 1.0 - Simple Implementation
**Module:** rwwi_astri_integration
