# OLT Data Migration Plan: PostgreSQL to Smallworld
## Phase 1: OLT Master Data Migration

**Version:** 1.0
**Date:** 2026-01-25
**Author:** Migration Team
**Status:** Planning

---

## 1. Executive Summary

This document outlines the strategy and implementation plan for migrating OLT (Optical Line Terminal) master data from PostgreSQL database to Smallworld GIS as `mit_hub` objects. This is Phase 1 of a multi-phase migration project that will include Feeder, Subfeeder, and Cluster designs in subsequent phases.

**Migration Flow:**
```
Phase 1: OLT Data (dim_olt_master_smallworld → mit_hub)
Phase 2: Feeder Design Data
Phase 3: Subfeeder Design Data
Phase 4: Cluster Design Data
```

---

## 2. Source Data Analysis

### 2.1 PostgreSQL Source Table

**Database Connection:**
- **Host:** 172.17.52.32
- **Port:** 5432
- **Database:** postgres
- **User:** iotech_data
- **Password:** Hmjl2MV8d!
- **Table:** `dim_olt_master_smallworld`

### 2.2 Source Schema

| Field Name | Data Type | Description | Required | Notes |
|------------|-----------|-------------|----------|-------|
| olt_id | Integer | Primary key | Yes | Unique identifier |
| device_id | String/Text | Device unique ID | Yes | |
| olt_code | String | OLT code | Yes | **Key field** |
| olt_boss_code | String | BOSS system code | No | |
| bng_code | String | BNG code | No | |
| category | String | OLT category | No | |
| site_code | String | Site identifier | No | |
| olt_hostname | String | Hostname | No | |
| ip_address | String | Management IP | No | |
| nms_ip_address | String | NMS IP | No | |
| type | String | OLT type/model | No | |
| olt_name | String | Display name | Yes | |
| olt_label | String | Label/alias | No | |
| latitude | Float/Number | GPS latitude | **Yes** | **Required for GIS** |
| longitude | Float/Number | GPS longitude | **Yes** | **Required for GIS** |
| olt_area | String | Area/region | No | |
| olt_brand | String | Manufacturer | No | |
| bng_area | String | BNG area | No | |
| owner | String | Owner organization | No | |
| system_device_version | String | Software version | No | |
| is_backup_uplink_available | Boolean/Integer | Backup availability | No | 0/1 or true/false |
| olt_tacacs_configured | Boolean/Integer | TACACS config status | No | 0/1 or true/false |
| rfs_date | DateTime | Ready for service date | No | |
| nms_integration_date | DateTime | NMS integration date | No | |
| target_rfs_period | DateTime | Target RFS period | No | |
| created_timestamp | DateTime | Record creation time | No | Audit field |
| updated_timestamp | DateTime | Last update time | No | Audit field |

---

## 3. Target Smallworld Object Analysis

### 3.1 Target Object: `mit_hub`

**Reference:** Similar to aerial OLT migration pattern

**Expected Fields/Attributes:**
- `name` - From `olt_code` or `olt_name`
- `olt_code` - From `olt_code` (primary identifier)
- `device_id` - From `device_id`
- `olt_boss_code` - From `olt_boss_code`
- `bng_code` - From `bng_code`
- `olt_hostname` - From `olt_hostname`
- `ip_address` - From `ip_address`
- `nms_ip_address` - From `nms_ip_address`
- `olt_type` - From `type`
- `olt_brand` - From `olt_brand`
- `category` - From `category`
- `site_code` - From `site_code`
- `olt_area` - From `olt_area`
- `bng_area` - From `bng_area`
- `owner` - From `owner`
- `system_device_version` - From `system_device_version`
- `is_backup_uplink_available` - From `is_backup_uplink_available`
- `olt_tacacs_configured` - From `olt_tacacs_configured`
- `rfs_date` - From `rfs_date`
- `nms_integration_date` - From `nms_integration_date`
- `target_rfs_period` - From `target_rfs_period`
- **Geometry:** Point location from `latitude` and `longitude`

### 3.2 Coordinate System

**Source Coordinates:** WGS84 (latitude/longitude in decimal degrees)
**Target Projection:** To be confirmed - likely EPSG:4326 (WGS84) or local projection
**Transformation Required:** Yes, if target uses different coordinate system

---

## 4. Field Mapping Strategy

### 4.1 Direct Mappings

| PostgreSQL Field | Smallworld Field | Transformation | Notes |
|------------------|------------------|----------------|-------|
| olt_code | olt_code | None | Primary identifier |
| olt_code | name | None | Use as object name |
| device_id | device_id | None | |
| olt_boss_code | olt_boss_code | None | |
| bng_code | bng_code | None | |
| olt_hostname | olt_hostname | None | |
| ip_address | ip_address | None | |
| nms_ip_address | nms_ip_address | None | |
| type | olt_type | None | |
| olt_name | olt_label | None | Alternative name |
| olt_brand | olt_brand | None | |
| category | category | None | |
| site_code | site_code | None | |
| olt_area | olt_area | None | |
| bng_area | bng_area | None | |
| owner | owner | None | |

### 4.2 Coordinate Transformation

```
latitude + longitude → Geometry Point
- Create point geometry using coordinate factory
- Transform from WGS84 to target coordinate system
- Set as object location
```

### 4.3 Boolean Field Conversion

```magik
PostgreSQL (0/1) → Smallworld (_true/_false)
- is_backup_uplink_available: 1 → _true, 0 → _false
- olt_tacacs_configured: 1 → _true, 0 → _false
```

### 4.4 DateTime Conversion

```magik
PostgreSQL DateTime → Smallworld Date/Time
- Use date_time class for conversion
- Handle NULL values appropriately
```

---

## 5. Migration Architecture

### 5.1 Component Design

```
┌─────────────────────────────────────────────────────────┐
│                Migration Module Architecture            │
└─────────────────────────────────────────────────────────┘

┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│  PostgreSQL  │─────>│   Magik      │─────>│  Smallworld  │
│   Database   │      │  Migration   │      │   Database   │
│              │      │   Engine     │      │  (mit_hub)   │
└──────────────┘      └──────────────┘      └──────────────┘
       │                     │                      │
       │              ┌──────┴──────┐               │
       │              │             │               │
       v              v             v               v
┌──────────────┐ ┌─────────┐ ┌─────────┐ ┌──────────────┐
│ Source Query │ │Validator│ │  Logger │ │ Transaction  │
└──────────────┘ └─────────┘ └─────────┘ └──────────────┘
```

### 5.2 Migration Module Structure

```
rwwi_olt_migration/
├── module.def
├── source/
│   ├── load_list.txt
│   ├── olt_migration_engine.magik         # Main engine
│   ├── olt_migration_validator.magik      # Data validation
│   ├── olt_migration_transformer.magik    # Data transformation
│   ├── olt_migration_dialog.magik         # UI (optional)
│   └── olt_migration_logger.magik         # Logging/reporting
└── resources/
    └── base/
        └── data/
            └── migration_config.xml        # Configuration
```

---

## 6. Implementation Phases

### Phase 1.1: Infrastructure Setup (Week 1)
- [ ] Create migration module structure
- [ ] Implement PostgreSQL JDBC connection helper
- [ ] Create logging framework
- [ ] Set up test environment with sample data

### Phase 1.2: Data Extraction (Week 1-2)
- [ ] Implement PostgreSQL query methods
- [ ] Create data reader with pagination support
- [ ] Add connection pooling
- [ ] Implement error handling for DB connectivity

### Phase 1.3: Data Validation (Week 2)
- [ ] Validate required fields (olt_code, latitude, longitude)
- [ ] Validate coordinate ranges (-90 to 90 for lat, -180 to 180 for lon)
- [ ] Check for duplicate olt_code
- [ ] Validate IP address formats
- [ ] Create validation report

### Phase 1.4: Data Transformation (Week 2-3)
- [ ] Implement coordinate transformation
- [ ] Convert boolean fields
- [ ] Convert datetime fields
- [ ] Handle NULL/empty values
- [ ] Map all fields according to mapping table

### Phase 1.5: Smallworld Object Creation (Week 3)
- [ ] Create mit_hub objects in Smallworld
- [ ] Set geometry (point from coordinates)
- [ ] Set all attributes
- [ ] Handle duplicate detection
- [ ] Implement transaction management

### Phase 1.6: Testing & Validation (Week 4)
- [ ] Unit testing for each component
- [ ] Integration testing with sample data
- [ ] Full migration test with subset of data
- [ ] Validation of migrated objects in Smallworld
- [ ] Performance testing

### Phase 1.7: Production Migration (Week 5)
- [ ] Backup current database
- [ ] Execute migration in batches
- [ ] Monitor progress
- [ ] Generate migration report
- [ ] Validation and reconciliation

---

## 7. Technical Implementation Details

### 7.1 PostgreSQL Connection (Magik)

```magik
_method olt_migration_engine.connect_to_postgres()
    ## Connect to PostgreSQL database using JDBC

    _local jdbc_url << "jdbc:postgresql://172.17.52.32:5432/postgres"
    _local user << "iotech_data"
    _local password << "Hmjl2MV8d!"

    _local driver_class << "org.postgresql.Driver"

    _try
        # Load JDBC driver
        _local driver << java_class.invoke_java_method(
            driver_class,
            "forName",
            {driver_class})

        # Create connection
        _local conn << java_class.invoke_java_method(
            "java.sql.DriverManager",
            "getConnection",
            {jdbc_url, user, password})

        >> conn
    _when error
        write("ERROR: Cannot connect to PostgreSQL")
        _return _unset
    _endtry
_endmethod
```

### 7.2 Data Query

```magik
_method olt_migration_engine.fetch_olt_data(_optional limit, offset)
    ## Fetch OLT data from PostgreSQL
    ## Parameters:
    ##   limit - Number of records to fetch (default: all)
    ##   offset - Starting offset (default: 0)

    _local conn << _self.connect_to_postgres()
    _if conn _is _unset _then _return {} _endif

    _local sql << "SELECT * FROM dim_olt_master_smallworld"

    _if limit _isnt _unset
    _then
        sql +<< " LIMIT " + limit.write_string
        _if offset _isnt _unset
        _then
            sql +<< " OFFSET " + offset.write_string
        _endif
    _endif

    _local stmt << conn.createStatement()
    _local rs << stmt.executeQuery(sql)

    _local results << rope.new()

    _loop
        _if _not rs.next() _then _leave _endif

        _local row << property_list.new_with(
            :olt_id, rs.getInt("olt_id"),
            :device_id, rs.getString("device_id"),
            :olt_code, rs.getString("olt_code"),
            :olt_boss_code, rs.getString("olt_boss_code"),
            :latitude, rs.getDouble("latitude"),
            :longitude, rs.getDouble("longitude"),
            # ... add all fields
        )

        results.add(row)
    _endloop

    rs.close()
    stmt.close()
    conn.close()

    >> results
_endmethod
```

### 7.3 Validation

```magik
_method olt_migration_engine.validate_olt_record(record)
    ## Validate OLT record before migration
    ## Returns: {valid?, error_messages}

    _local errors << rope.new()

    # Required fields
    _if record[:olt_code] _is _unset _orif record[:olt_code] = ""
    _then
        errors.add("Missing olt_code")
    _endif

    # Coordinate validation
    _local lat << record[:latitude]
    _local lon << record[:longitude]

    _if lat _is _unset _orif lon _is _unset
    _then
        errors.add("Missing coordinates")
    _elif lat < -90 _orif lat > 90
    _then
        errors.add("Invalid latitude: " + lat.write_string)
    _elif lon < -180 _orif lon > 180
    _then
        errors.add("Invalid longitude: " + lon.write_string)
    _endif

    # Check for existing object
    _if _self.olt_exists?(record[:olt_code])
    _then
        errors.add("OLT already exists: " + record[:olt_code])
    _endif

    >> errors.empty?, errors
_endmethod
```

### 7.4 Object Creation

```magik
_method olt_migration_engine.create_mit_hub(record)
    ## Create mit_hub object from PostgreSQL record

    # Validate first
    _local (valid?, errors) << _self.validate_olt_record(record)
    _if _not valid?
    _then
        write("Validation failed for ", record[:olt_code])
        _for err _over errors.fast_elements()
        _loop
            write("  - ", err)
        _endloop
        _return _false, errors
    _endif

    # Create geometry
    _local lat << record[:latitude]
    _local lon << record[:longitude]
    _local coord << coordinate.new(lon, lat)  # Note: lon, lat order
    _local geom << pseudo_point.new(coord)

    # Get dataset
    _local ds << gis_program_manager.cached_dataset(:gis)
    _local mit_hub_table << ds.collections[:mit_hub]

    # Create object
    _local new_hub << mit_hub_table.new_detached_record()

    # Set geometry
    new_hub.location << geom

    # Set attributes
    new_hub.olt_code << record[:olt_code]
    new_hub.name << record[:olt_code]
    new_hub.device_id << record[:device_id]
    new_hub.olt_boss_code << record[:olt_boss_code]
    new_hub.bng_code << record[:bng_code]
    new_hub.olt_hostname << record[:olt_hostname]
    new_hub.ip_address << record[:ip_address]
    new_hub.nms_ip_address << record[:nms_ip_address]
    new_hub.olt_type << record[:type]
    new_hub.olt_brand << record[:olt_brand]
    new_hub.category << record[:category]
    new_hub.site_code << record[:site_code]
    new_hub.olt_area << record[:olt_area]
    new_hub.bng_area << record[:bng_area]
    new_hub.owner << record[:owner]

    # Boolean fields
    new_hub.is_backup_uplink_available << (record[:is_backup_uplink_available] = 1)
    new_hub.olt_tacacs_configured << (record[:olt_tacacs_configured] = 1)

    # DateTime fields
    _if record[:rfs_date] _isnt _unset
    _then
        new_hub.rfs_date << _self.convert_datetime(record[:rfs_date])
    _endif

    # Insert into database
    _try
        new_hub.source_collection.insert(new_hub)
        write("Created mit_hub: ", record[:olt_code])
        _return _true, _unset
    _when error
        write("ERROR creating mit_hub: ", record[:olt_code])
        write("  ", error.report_contents_string)
        _return _false, {error.report_contents_string}
    _endtry
_endmethod
```

---

## 8. Migration Execution Strategy

### 8.1 Batch Processing

**Batch Size:** 100 records per batch
**Rationale:** Balance between performance and transaction safety

```magik
_method olt_migration_engine.migrate_all_olts()
    ## Main migration method

    _local batch_size << 100
    _local offset << 0
    _local total_success << 0
    _local total_errors << 0
    _local error_log << rope.new()

    write("Starting OLT migration...")
    write("Batch size: ", batch_size)

    _loop
        # Fetch batch
        _local batch << _self.fetch_olt_data(batch_size, offset)

        _if batch.empty? _then _leave _endif

        write("Processing batch at offset ", offset, " (", batch.size, " records)")

        # Process each record in batch
        _for record _over batch.fast_elements()
        _loop
            _local (success?, errors) << _self.create_mit_hub(record)

            _if success?
            _then
                total_success +<< 1
            _else
                total_errors +<< 1
                error_log.add({record[:olt_code], errors})
            _endif
        _endloop

        # Commit batch
        gis_program_manager.cached_dataset(:gis).commit()

        offset +<< batch_size

        # Progress reporting
        write("Progress: ", total_success, " success, ", total_errors, " errors")
    _endloop

    # Final report
    _self.generate_migration_report(total_success, total_errors, error_log)

    write("Migration completed!")
    write("Total success: ", total_success)
    write("Total errors: ", total_errors)
_endmethod
```

### 8.2 Transaction Management

- **Commit per batch:** Each batch of 100 records is committed separately
- **Rollback on error:** Individual record errors don't rollback entire batch
- **Manual rollback:** Keep backup for full database rollback if needed

### 8.3 Error Handling

```magik
Error Levels:
1. WARNING - Non-critical issues (logged, migration continues)
2. ERROR - Record skipped (logged, migration continues)
3. FATAL - Migration stopped (requires manual intervention)

Examples:
- Missing optional field → WARNING
- Invalid coordinates → ERROR (skip record)
- Database connection lost → FATAL (stop migration)
```

---

## 9. Validation & Reconciliation

### 9.1 Pre-Migration Validation

```sql
-- Count total records in PostgreSQL
SELECT COUNT(*) FROM dim_olt_master_smallworld;

-- Check for missing required fields
SELECT COUNT(*) FROM dim_olt_master_smallworld
WHERE olt_code IS NULL OR olt_code = '';

-- Check for missing coordinates
SELECT COUNT(*) FROM dim_olt_master_smallworld
WHERE latitude IS NULL OR longitude IS NULL;

-- Check for invalid coordinates
SELECT COUNT(*) FROM dim_olt_master_smallworld
WHERE latitude < -90 OR latitude > 90
   OR longitude < -180 OR longitude > 180;

-- Check for duplicates
SELECT olt_code, COUNT(*)
FROM dim_olt_master_smallworld
GROUP BY olt_code
HAVING COUNT(*) > 1;
```

### 9.2 Post-Migration Validation

```magik
_method olt_migration_engine.validate_migration()
    ## Validate migration results

    # Count Smallworld objects
    _local ds << gis_program_manager.cached_dataset(:gis)
    _local mit_hub_col << ds.collections[:mit_hub]
    _local sw_count << mit_hub_col.size

    # Count PostgreSQL records
    _local pg_count << _self.get_postgres_olt_count()

    write("PostgreSQL records: ", pg_count)
    write("Smallworld objects: ", sw_count)
    write("Difference: ", pg_count - sw_count)

    # Sample verification (10 random records)
    _local samples << _self.get_random_olt_codes(10)
    _for olt_code _over samples.fast_elements()
    _loop
        _local pg_record << _self.fetch_olt_by_code(olt_code)
        _local sw_object << mit_hub_col.select(predicate.eq(:olt_code, olt_code)).an_element()

        _if sw_object _is _unset
        _then
            write("Missing in Smallworld: ", olt_code)
        _else
            # Verify key fields match
            _self.compare_records(pg_record, sw_object)
        _endif
    _endloop
_endmethod
```

---

## 10. Rollback Strategy

### 10.1 Backup Plan

**Before Migration:**
```magik
# Backup current Smallworld database
# Use Smallworld backup utilities
# Keep backup for at least 7 days after migration
```

### 10.2 Rollback Procedure

**If migration needs to be rolled back:**

```magik
_method olt_migration_engine.rollback_migration()
    ## Delete all migrated OLT objects

    _local ds << gis_program_manager.cached_dataset(:gis)
    _local mit_hub_col << ds.collections[:mit_hub]

    # Identify migrated objects (have olt_code populated)
    _local migrated << mit_hub_col.select(
        predicate.neq(:olt_code, _unset))

    write("Found ", migrated.size, " migrated objects to delete")

    _if _self.confirm_rollback()
    _then
        _for hub _over migrated.fast_elements()
        _loop
            hub.delete()
        _endloop

        ds.commit()
        write("Rollback completed")
    _endif
_endmethod
```

---

## 11. Monitoring & Reporting

### 11.1 Progress Logging

```magik
Migration Log Format:
[TIMESTAMP] [LEVEL] [COMPONENT] Message

Example:
2026-01-25 10:30:15 INFO  MIGRATION Starting OLT migration
2026-01-25 10:30:20 INFO  FETCH    Fetched batch 0-100 (100 records)
2026-01-25 10:30:25 ERROR CREATE   Failed to create OLT-001: Invalid coordinates
2026-01-25 10:30:45 INFO  BATCH    Batch 0 completed: 99 success, 1 error
```

### 11.2 Migration Report

```
OLT Migration Report
====================
Date: 2026-01-25 10:30:00
Duration: 2 hours 15 minutes

Summary:
--------
Total Records in PostgreSQL: 1,250
Successfully Migrated: 1,235
Errors: 15
Success Rate: 98.8%

Error Breakdown:
----------------
- Missing coordinates: 5
- Invalid coordinates: 3
- Duplicate olt_code: 4
- Database errors: 3

Failed Records:
---------------
1. OLT-001 - Missing coordinates
2. OLT-045 - Invalid latitude: 95.5
3. OLT-089 - Duplicate olt_code
...

Recommendations:
----------------
1. Review and fix 15 failed records in PostgreSQL
2. Re-run migration for failed records only
3. Validate migrated objects in GIS
```

---

## 12. Risk Assessment & Mitigation

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Data loss during migration | High | Low | Database backup before migration |
| Invalid coordinates | Medium | Medium | Pre-migration validation, skip invalid records |
| Database connection failure | High | Low | Connection retry logic, transaction management |
| Duplicate records | Low | Medium | Duplicate detection, skip or update strategy |
| Performance issues | Medium | Medium | Batch processing, optimized queries |
| Incomplete migration | Medium | Low | Progress logging, resume capability |

---

## 13. Dependencies

### 13.1 Software Requirements

- Smallworld GIS 5.x
- PostgreSQL JDBC driver (included in Java runtime)
- Java 17 (already available)
- Magik runtime

### 13.2 Access Requirements

- PostgreSQL database read access
- Smallworld write access to target dataset
- Network connectivity to PostgreSQL server

### 13.3 Data Requirements

- Complete and clean OLT data in PostgreSQL
- Valid coordinates for all OLT records
- No duplicate olt_code values

---

## 14. Success Criteria

Migration is considered successful when:

- [ ] ≥ 95% of PostgreSQL records migrated successfully
- [ ] All migrated objects have valid geometries
- [ ] All required fields populated correctly
- [ ] Coordinate locations verified in GIS
- [ ] Migration report generated and reviewed
- [ ] Sample validation confirms data accuracy
- [ ] No data corruption in Smallworld database
- [ ] Rollback procedure tested and documented

---

## 15. Next Steps (Future Phases)

### Phase 2: Feeder Design Migration
- Table: TBD
- Target Object: TBD
- Dependencies: Completed OLT migration (foreign key to OLT)

### Phase 3: Subfeeder Design Migration
- Table: TBD
- Target Object: TBD
- Dependencies: Completed Feeder migration

### Phase 4: Cluster Design Migration
- Table: TBD
- Target Object: TBD
- Dependencies: Completed Subfeeder migration

---

## 16. Appendix

### A. Sample PostgreSQL Query

```sql
-- Sample query to fetch OLT data
SELECT
    olt_id,
    device_id,
    olt_code,
    olt_boss_code,
    bng_code,
    category,
    site_code,
    olt_hostname,
    ip_address,
    nms_ip_address,
    type,
    olt_name,
    olt_label,
    latitude,
    longitude,
    olt_area,
    olt_brand,
    bng_area,
    owner,
    system_device_version,
    is_backup_uplink_available,
    olt_tacacs_configured,
    rfs_date,
    nms_integration_date,
    target_rfs_period,
    created_timestamp,
    updated_timestamp
FROM dim_olt_master_smallworld
WHERE latitude IS NOT NULL
  AND longitude IS NOT NULL
ORDER BY olt_code;
```

### B. Coordinate Validation Query

```sql
-- Validate coordinates
SELECT
    olt_code,
    latitude,
    longitude,
    CASE
        WHEN latitude IS NULL THEN 'Missing latitude'
        WHEN longitude IS NULL THEN 'Missing longitude'
        WHEN latitude < -90 OR latitude > 90 THEN 'Invalid latitude range'
        WHEN longitude < -180 OR longitude > 180 THEN 'Invalid longitude range'
        ELSE 'Valid'
    END AS validation_status
FROM dim_olt_master_smallworld
WHERE latitude IS NULL
   OR longitude IS NULL
   OR latitude < -90
   OR latitude > 90
   OR longitude < -180
   OR longitude > 180;
```

### C. Duplicate Check Query

```sql
-- Find duplicate olt_code
SELECT
    olt_code,
    COUNT(*) as count
FROM dim_olt_master_smallworld
GROUP BY olt_code
HAVING COUNT(*) > 1
ORDER BY count DESC;
```

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-01-25 | Migration Team | Initial plan created |

---

**END OF DOCUMENT**
