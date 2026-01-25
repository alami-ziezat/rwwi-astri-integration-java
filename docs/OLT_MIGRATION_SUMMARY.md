# OLT Migration - Quick Summary

## What It Does

Migrates OLT (Optical Line Terminal) data from PostgreSQL to Smallworld GIS.

**Source:** PostgreSQL table `dim_olt_master_smallworld` (172.17.52.32:5432)
**Target:** Smallworld `mit_hub` collection
**Records:** All OLT records with valid olt_code and coordinates

---

## How to Run

### 1. Prerequisites
- PostgreSQL connection configured (already done in `connection_external_ds.cfg`)
- Smallworld database backup completed
- Environment variables set:
  - `DATA_SOURCE_ASTRI` = `jdbc:postgresql://172.17.52.32:5432/postgres`
  - `POSTGRESQL_JDBC_PATH` = Path to PostgreSQL JDBC driver

### 2. Execute Migration
```magik
ds << gis_program_manager.cached_dataset(:gis)
migrator << astri_data_migrator.new(ds)
migrator.migrate_olts()
```

### 3. Verify Results
```magik
mit_hub_col << ds.collections[:mit_hub]
write("Total OLTs: ", mit_hub_col.size)
```

---

## Key Features

✅ **No Duplicates:** Checks `olt_code` before creating (skips if exists)
✅ **Coordinate Validation:** Skips records with NULL/missing lat/lon or (0.0, 0.0)
✅ **Coordinate Transform:** WGS84 lat/lon → Local coordinate system
✅ **Batch Commit:** Commits every 1000 records for better performance
✅ **Safe & Idempotent:** Can run multiple times safely
✅ **Field Mapping:** Maps 25 Smallworld fields (11 mapped, 3 constants)
✅ **Notes Aggregation:** Consolidates unmapped fields into pipe-delimited notes
✅ **Error Handling:** Continues on errors, reports at end

---

## Implementation Files

| File | Purpose |
|------|---------|
| `astri_data_migrator.magik` | Main migration code |
| `OLT_MIGRATION_USAGE.md` | Detailed usage guide |
| `olt_migration_example.magik` | Example script to run |
| `OLT_MIGRATION_SUMMARY.md` | This file (quick reference) |

---

## What Gets Migrated

**Required Fields (PostgreSQL → Smallworld):**
- `olt_code` → `olt_code` (primary identifier)
- `latitude`, `longitude` → `location` (geometry)

**Mapped Fields:**
- `olt_name` → `name` (display name)
- `olt_label` → `olt` (OLT label)
- `device_id` → `object_id` (device identifier)
- `owner` → `asset_owner` ("MYREPUBLIC"="Owned", else "Third Party")
- `olt_area` → `region` (OLT area/region)
- `bng_area` → `sub_region` (BNG area)
- `bng_code` → `segment` (BNG code)
- `olt_hostname` → `span` (OLT hostname)
- All other fields → `notes` (pipe-delimited: IP, NMS IP, Brand, Type, BOSS Code, Category, Site, Version, Backup Uplink, TACACS, RFS dates)

**Constant Values:**
- "OLT" → `type`
- "In Service" → `construction_status`
- "Primary" → `fttx_network_type`

**Fields Set to _unset:**
- `floor_plan`, `mit_structure_point`, `folders`, `project`
- `stf_item_code`, `cluster`, `ring_name`, `jalur`, `line_type`, `uuid`

**Fields Set to Empty:**
- `annotation_2` (no corresponding PostgreSQL field)

**Total:** 25 Smallworld fields (10 mapped, 3 constants, 1 empty, 11 unset)

---

## Next Steps After OLT Migration

Once OLT migration is complete and verified:
1. **Phase 2:** Feeder design data migration (references OLT)
2. **Phase 3:** Subfeeder design data migration (references Feeder)
3. **Phase 4:** Cluster design data migration (references Subfeeder)

---

## Troubleshooting Quick Fixes

| Problem | Solution |
|---------|----------|
| Connection failed | Check environment variables, verify PostgreSQL is running |
| NULL coordinates | Records with NULL lat/lon are skipped - fix in PostgreSQL before migration |
| Invalid coordinates (0,0) | Records with (0.0, 0.0) are skipped - fix in PostgreSQL |
| Invalid olt_code | Fix empty/NULL olt_code values in PostgreSQL |
| Coordinate error | Verify lat/lon ranges: (-90 to 90), (-180 to 180) |

---

**Created:** 2026-01-25
**Module:** `rwwi_astri_integration`
**Complexity:** Simple (Phase 1 only - complex work is for Phases 2-4)
