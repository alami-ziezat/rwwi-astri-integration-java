# OLT Data Migration Documentation

This directory contains comprehensive documentation for migrating OLT (Optical Line Terminal) and network design data from PostgreSQL to Smallworld GIS.

---

## Document Overview

### 📋 [Migration Plan - OLT to Smallworld](migration_plan_olt_to_smallworld.md)
**Audience:** Technical team, project managers, stakeholders
**Purpose:** Complete technical specification and implementation plan

**Contents:**
- Executive Summary
- Source and target data analysis
- Field mapping strategy
- Architecture design
- Implementation phases (week-by-week)
- Technical code examples
- Validation and reconciliation procedures
- Risk assessment
- Rollback strategy
- Success criteria

**When to use:**
- Planning the migration project
- Understanding technical requirements
- Reviewing implementation approach
- Risk assessment and mitigation planning

---

### ✅ [Implementation Checklist - OLT](migration_checklist_olt.md)
**Audience:** Developers, QA team, database administrators
**Purpose:** Step-by-step execution guide

**Contents:**
- Pre-migration preparation tasks
- Module development checklist
- Testing procedures
- Migration execution steps
- Post-migration validation
- Rollback procedures
- SQL and Magik reference commands
- Sign-off template

**When to use:**
- During implementation
- Before/during/after migration execution
- For progress tracking
- For quality assurance

---

## Migration Phases

```
┌─────────────────────────────────────────────────┐
│           Multi-Phase Migration Plan            │
└─────────────────────────────────────────────────┘

Phase 1: OLT Master Data ← YOU ARE HERE
├── Source: dim_olt_master_smallworld (PostgreSQL)
├── Target: mit_hub (Smallworld)
├── Status: Planning
└── Documents: ✓ Plan + ✓ Checklist

Phase 2: Feeder Design Data
├── Source: TBD
├── Target: TBD
├── Status: Pending
└── Dependencies: Completed Phase 1

Phase 3: Subfeeder Design Data
├── Source: TBD
├── Target: TBD
├── Status: Pending
└── Dependencies: Completed Phase 2

Phase 4: Cluster Design Data
├── Source: TBD
├── Target: TBD
├── Status: Pending
└── Dependencies: Completed Phase 3
```

---

## Quick Start Guide

### For Project Managers
1. Read **Section 1-2** of the Migration Plan (Executive Summary + Source Data)
2. Review **Section 6** (Implementation Phases) for timeline
3. Review **Section 12** (Risk Assessment)
4. Use the Implementation Checklist to track progress

### For Developers
1. Read **Section 3-5** of the Migration Plan (Target Object, Mapping, Architecture)
2. Review **Section 7** (Technical Implementation) for code examples
3. Follow the **Module Development Checklist** step-by-step
4. Use **Testing Checklist** for quality assurance

### For Database Administrators
1. Review **Section 2** (Source Data Analysis)
2. Run **Pre-Migration SQL queries** from Section 9.1
3. Ensure database backup before migration
4. Follow **Migration Execution Checklist**

### For QA Team
1. Review **Section 9** (Validation & Reconciliation)
2. Follow **Testing Checklist** in implementation document
3. Execute post-migration validation procedures
4. Generate validation report

---

## Key Information at a Glance

### PostgreSQL Database
- **Host:** 172.17.52.32
- **Port:** 5432
- **Database:** postgres
- **User:** iotech_data
- **Table:** `dim_olt_master_smallworld`

### Smallworld Target
- **Object:** `mit_hub`
- **Dataset:** `:gis`
- **Key Field:** `olt_code`
- **Geometry:** Point (from latitude/longitude)

### Critical Fields
**Required for Migration:**
- olt_code (identifier)
- latitude (coordinate)
- longitude (coordinate)

**Important Attributes:**
- device_id, olt_name
- ip_address, nms_ip_address
- olt_brand, olt_type
- rfs_date

### Timeline Estimate
- **Module Development:** 3 weeks
- **Testing:** 1 week
- **Production Migration:** 1 week
- **Total:** ~5 weeks

### Success Criteria
- ≥ 95% migration success rate
- Valid geometries for all migrated objects
- All required fields populated
- Post-migration validation passed

---

## Migration Architecture

```
┌─────────────────────────────────────────────────────────┐
│                  Migration Flow Diagram                 │
└─────────────────────────────────────────────────────────┘

    PostgreSQL                Magik Engine           Smallworld
    ┌─────────┐              ┌──────────┐           ┌─────────┐
    │  DIM_   │              │          │           │  mit_   │
    │  OLT_   │─── Query ───>│ Validate │           │  hub    │
    │ MASTER  │              │          │           │         │
    │         │              │Transform │           │         │
    │         │              │          │           │         │
    │         │              │  Create  │─── Write ─>│         │
    └─────────┘              └──────────┘           └─────────┘
         │                        │                      │
         │                        │                      │
         v                        v                      v
    [Batch Read]            [Batch Process]        [Batch Commit]
    100 records             Validate each          Commit per batch
                            Transform each
                            Log errors
```

---

## Pre-Migration Validation Queries

Run these queries before starting migration:

```sql
-- 1. Total records
SELECT COUNT(*) as total_records
FROM dim_olt_master_smallworld;

-- 2. Records with missing coordinates
SELECT COUNT(*) as missing_coords
FROM dim_olt_master_smallworld
WHERE latitude IS NULL OR longitude IS NULL;

-- 3. Records with invalid coordinates
SELECT COUNT(*) as invalid_coords
FROM dim_olt_master_smallworld
WHERE latitude < -90 OR latitude > 90
   OR longitude < -180 OR longitude > 180;

-- 4. Duplicate olt_code
SELECT COUNT(*) as duplicate_olts
FROM (
    SELECT olt_code
    FROM dim_olt_master_smallworld
    GROUP BY olt_code
    HAVING COUNT(*) > 1
) duplicates;

-- 5. Missing required fields
SELECT COUNT(*) as missing_required
FROM dim_olt_master_smallworld
WHERE olt_code IS NULL OR olt_code = '';
```

**Expected Results:**
- Total records: > 0
- Missing coords: 0
- Invalid coords: 0
- Duplicate olts: 0
- Missing required: 0

---

## Common Issues and Solutions

### Issue: PostgreSQL Connection Failed
**Solution:**
1. Check network connectivity: `ping 172.17.52.32`
2. Verify PostgreSQL is running
3. Check credentials
4. Verify JDBC driver is loaded

### Issue: Invalid Coordinates
**Solution:**
1. Run coordinate validation query
2. Fix invalid records in PostgreSQL
3. Re-run pre-migration validation

### Issue: Duplicate olt_code
**Solution:**
1. Identify duplicates with SQL query
2. Resolve duplicates in source database
3. Decide on merge or update strategy

### Issue: Geometry Creation Failed
**Solution:**
1. Verify coordinate transformation
2. Check coordinate order (longitude, latitude)
3. Verify coordinate system configuration

---

## Reference Materials

### External Documentation
- Smallworld GIS 5.x Documentation
- PostgreSQL JDBC Driver Documentation
- Magik Language Reference (see CLAUDE.md)

### Internal References
- CLAUDE.md - Smallworld and Magik basics
- Aerial OLT migration (previous project) - for mit_hub reference

---

## Version History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-01-25 | Migration Team | Initial documentation created |

---

## Contact & Support

For questions or issues during migration:

1. **Technical Issues:** Review troubleshooting section in migration plan
2. **Data Quality Issues:** Contact database administrator
3. **Smallworld Issues:** Contact GIS administrator
4. **Project Status:** Contact project manager

---

## Implementation

### Phase 1: OLT Migration - COMPLETED ✅

**Implementation File:** `astri_data_migrator.magik`
**Location:** `rwwi_astri_integration/source/`

Simple implementation that:
- Connects to PostgreSQL `dim_olt_master_smallworld` table
- Validates required fields (olt_code, lat/lon not NULL)
- Skips records with NULL coordinates or invalid (0.0, 0.0)
- Checks if OLT exists by `olt_code` (no duplicates)
- Transforms WGS84 lat/lon to local coordinates
- Creates `mit_hub` objects with proper field mapping (25 fields)
- **Batch commits every 1000 records** for better performance and safety
- Maps 11 fields: name, olt, olt_code, object_id, asset_owner (conditional), region, sub_region, segment, span, annotation_2, notes (pipe-delimited)
- Sets 3 constants: type="OLT", construction_status="In Service", fttx_network_type="Primary"
- Consolidates unmapped fields into notes field (IP, Brand, Model, Ports, etc.)

**Usage Guide:** See `OLT_MIGRATION_USAGE.md` for complete instructions

**Quick Start:**
```magik
# Get database
ds << gis_program_manager.cached_dataset(:gis)

# Create migrator
migrator << astri_data_migrator.new(ds)

# Run migration
migrator.migrate_olts()
```

**Example Script:** See `olt_migration_example.magik`

### Next Phases

2. **Feeder Design Data** - Future implementation
3. **Subfeeder Design Data** - Future implementation
4. **Cluster Design Data** - Future implementation

---

**Last Updated:** 2026-01-25
**Status:** Phase 1 (OLT) Implementation Complete
