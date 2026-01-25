# OLT Migration Implementation Checklist

**Project:** PostgreSQL to Smallworld OLT Migration
**Phase:** 1 - OLT Master Data
**Target Object:** `mit_hub`

---

## Pre-Migration Checklist

### Database Access
- [ ] Verify PostgreSQL connection (172.17.52.32:5432)
- [ ] Test credentials (iotech_data / Hmjl2MV8d!)
- [ ] Confirm access to `dim_olt_master_smallworld` table
- [ ] Run record count query
- [ ] Verify JDBC driver availability

### Data Quality Check
- [ ] Run pre-migration SQL validation queries
- [ ] Check for missing olt_code values
- [ ] Check for missing coordinates (latitude/longitude)
- [ ] Validate coordinate ranges (-90 to 90, -180 to 180)
- [ ] Check for duplicate olt_code
- [ ] Review and fix invalid records

### Smallworld Environment
- [ ] Verify `mit_hub` object exists in target dataset
- [ ] Check field mappings match target schema
- [ ] Verify write permissions to dataset
- [ ] Test coordinate transformation
- [ ] Create backup of current database

---

## Module Development Checklist

### Infrastructure (Week 1)
- [ ] Create `rwwi_olt_migration` module
- [ ] Create module.def file
- [ ] Set up source directory structure
- [ ] Implement PostgreSQL JDBC connection helper
- [ ] Create logging utility
- [ ] Set up error handling framework

### Data Layer (Week 1-2)
- [ ] Implement `fetch_olt_data()` method
- [ ] Add pagination support (limit/offset)
- [ ] Test query with sample records
- [ ] Implement connection pooling
- [ ] Add retry logic for connection failures

### Validation Layer (Week 2)
- [ ] Implement `validate_olt_record()` method
- [ ] Add required field validation
- [ ] Add coordinate range validation
- [ ] Add duplicate detection
- [ ] Add IP address format validation (optional)
- [ ] Create validation report generator

### Transformation Layer (Week 2-3)
- [ ] Implement coordinate to geometry conversion
- [ ] Add boolean field conversion (0/1 → _true/_false)
- [ ] Add datetime conversion
- [ ] Handle NULL/empty values
- [ ] Test all transformations

### Object Creation Layer (Week 3)
- [ ] Implement `create_mit_hub()` method
- [ ] Set geometry from coordinates
- [ ] Map all PostgreSQL fields to Smallworld attributes
- [ ] Add transaction management
- [ ] Test with sample records

### Batch Processing (Week 3)
- [ ] Implement `migrate_all_olts()` method
- [ ] Add batch size configuration (default: 100)
- [ ] Add progress logging
- [ ] Implement commit per batch
- [ ] Add resume capability for failed batches

---

## Testing Checklist

### Unit Testing
- [ ] Test PostgreSQL connection
- [ ] Test single record fetch
- [ ] Test batch fetch with pagination
- [ ] Test coordinate validation
- [ ] Test coordinate to geometry conversion
- [ ] Test boolean conversion
- [ ] Test datetime conversion
- [ ] Test object creation

### Integration Testing
- [ ] Test end-to-end migration with 10 records
- [ ] Verify objects created in Smallworld
- [ ] Verify geometry locations on map
- [ ] Verify all attributes populated correctly
- [ ] Test error handling (invalid coordinates)
- [ ] Test duplicate detection

### Performance Testing
- [ ] Test batch processing with 1000 records
- [ ] Measure migration speed (records per minute)
- [ ] Monitor memory usage
- [ ] Test transaction commit performance

---

## Migration Execution Checklist

### Day Before Migration
- [ ] Notify stakeholders
- [ ] Schedule maintenance window
- [ ] Backup Smallworld database
- [ ] Verify PostgreSQL data is latest
- [ ] Prepare rollback procedure
- [ ] Set up monitoring

### Migration Day - Pre-Execution
- [ ] Final database backup
- [ ] Run pre-migration validation SQL
- [ ] Review validation results
- [ ] Fix critical data issues
- [ ] Clear Smallworld cache
- [ ] Start logging

### Migration Day - Execution
- [ ] Start migration at agreed time
- [ ] Monitor progress logs
- [ ] Watch for errors
- [ ] Note any warnings
- [ ] Monitor database performance
- [ ] Record start time

### Migration Day - Post-Execution
- [ ] Record end time
- [ ] Generate migration report
- [ ] Review error log
- [ ] Run post-migration validation
- [ ] Compare record counts
- [ ] Sample verification (10 random records)
- [ ] Visual inspection in GIS

---

## Post-Migration Checklist

### Validation
- [ ] Compare PostgreSQL count vs Smallworld count
- [ ] Verify all olt_code values migrated
- [ ] Check geometry locations on map
- [ ] Verify attribute values for sample records
- [ ] Check for orphaned records
- [ ] Validate coordinate transformations

### Documentation
- [ ] Generate final migration report
- [ ] Document any issues encountered
- [ ] Document failed records (if any)
- [ ] Update migration statistics
- [ ] Archive migration logs
- [ ] Document lessons learned

### Communication
- [ ] Notify stakeholders of completion
- [ ] Share migration report
- [ ] Report success rate
- [ ] Explain any failures
- [ ] Provide access to new data

### Cleanup
- [ ] Archive migration logs
- [ ] Keep backup for 7 days
- [ ] Document rollback procedure
- [ ] Update documentation

---

## Rollback Checklist (If Needed)

- [ ] Stop all access to migrated data
- [ ] Backup current state (with migrated data)
- [ ] Restore from pre-migration backup
- [ ] Verify restoration successful
- [ ] Document rollback reason
- [ ] Plan for re-migration

---

## Code Review Checklist

### Code Quality
- [ ] All methods have documentation comments
- [ ] Error handling implemented
- [ ] Logging statements added
- [ ] No hardcoded values (use configuration)
- [ ] Transaction management correct
- [ ] Memory-efficient (no unnecessary collections)

### Testing Coverage
- [ ] Unit tests for all methods
- [ ] Integration tests pass
- [ ] Error scenarios tested
- [ ] Edge cases handled

### Performance
- [ ] Batch processing implemented
- [ ] Pagination used for large datasets
- [ ] Connection pooling configured
- [ ] Memory usage acceptable

---

## SQL Queries for Reference

### Count Total Records
```sql
SELECT COUNT(*) FROM dim_olt_master_smallworld;
```

### Check Missing Required Fields
```sql
SELECT COUNT(*) FROM dim_olt_master_smallworld
WHERE olt_code IS NULL OR olt_code = ''
   OR latitude IS NULL OR longitude IS NULL;
```

### Find Invalid Coordinates
```sql
SELECT olt_code, latitude, longitude
FROM dim_olt_master_smallworld
WHERE latitude < -90 OR latitude > 90
   OR longitude < -180 OR longitude > 180;
```

### Find Duplicates
```sql
SELECT olt_code, COUNT(*)
FROM dim_olt_master_smallworld
GROUP BY olt_code
HAVING COUNT(*) > 1;
```

---

## Magik Commands for Reference

### Count Smallworld Objects
```magik
ds << gis_program_manager.cached_dataset(:gis)
mit_hub_col << ds.collections[:mit_hub]
write("Total mit_hub objects: ", mit_hub_col.size)
```

### Test Coordinate Conversion
```magik
lat << 3.1390  # Example latitude
lon << 101.6869  # Example longitude
coord << coordinate.new(lon, lat)
geom << pseudo_point.new(coord)
write("Created point: ", geom)
```

### Sample Query
```magik
ds << gis_program_manager.cached_dataset(:gis)
mit_hub_col << ds.collections[:mit_hub]
sample << mit_hub_col.select(predicate.eq(:olt_code, "OLT-001")).an_element()
_if sample _isnt _unset
_then
    write("Found: ", sample.olt_code)
    write("Location: ", sample.location)
_endif
```

---

## Key Metrics to Track

- **Total Records in PostgreSQL:** _______
- **Total Migrated to Smallworld:** _______
- **Success Rate:** _______%
- **Migration Duration:** _______ hours
- **Average Speed:** _______ records/minute
- **Failed Records:** _______
- **Validation Pass Rate:** _______%

---

## Contact Information

**Database Admin:** _______________________
**GIS Admin:** _______________________
**Developer:** _______________________
**Project Manager:** _______________________

---

## Sign-off

- [ ] Pre-migration validation complete
- [ ] Migration execution successful
- [ ] Post-migration validation passed
- [ ] Documentation complete
- [ ] Stakeholders notified

**Completed By:** _______________________
**Date:** _______________________
**Signature:** _______________________

---

**END OF CHECKLIST**
