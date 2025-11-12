# KMZ UUID Database Retrieval Implementation Plan

## Date: 2025-11-10
## Status: ✅ IMPLEMENTED

## Overview
Implement KMZ UUID retrieval from PostgreSQL database for work orders. The KMZ UUID is currently set to empty string in the API response and needs to be populated from the database based on infrastructure type and cluster/subfeeder/feeder code.

## Database Connection

### Connection String
```
[POSTGRESQL_ASTRI_DB]
```

### Connection Method
```magik
# Note: rwwi_external_ds_manager is in the user package
(is_connect?, connection) << user:rwwi_external_ds_manager.open_connection_for("[POSTGRESQL_ASTRI_DB]")
```

## Database Schema

### Tables by Infrastructure Type

| Infrastructure Type | Table Name |
|-------------------|------------|
| cluster | `dim_cluster_master_smallworld` |
| subfeeder | `dim_subfeeder_master_smallworld` |
| feeder | `dim_feeder_master_smallworld` |

### KMZ UUID Fields

**Priority Order:**
1. `abd_kmz_uuid` - Use this if NOT NULL
2. `apd_kmz_uuid` - Use this as fallback

**Selection Logic:**
```sql
COALESCE(abd_kmz_uuid, apd_kmz_uuid) AS kmz_uuid
```

## SQL Queries

### Cluster Query
```sql
SELECT COALESCE(abd_kmz_uuid, apd_kmz_uuid) AS kmz_uuid
FROM dim_cluster_master_smallworld
WHERE cluster_code = ?
LIMIT 1
```

### Subfeeder Query
```sql
SELECT COALESCE(abd_kmz_uuid, apd_kmz_uuid) AS kmz_uuid
FROM dim_subfeeder_master_smallworld
WHERE subfeeder_code = ?
LIMIT 1
```

### Feeder Query
```sql
SELECT COALESCE(abd_kmz_uuid, apd_kmz_uuid) AS kmz_uuid
FROM dim_feeder_master_smallworld
WHERE feeder_code = ?
LIMIT 1
```

## Implementation Design

### 1. Shared Constants for SQL Queries

Define SQL queries as shared constants (following `rwi_pni_to_tenoss` pattern):

```magik
rwwi_astri_workorder_engine.define_shared_constant(:sql_for_kmz_uuid,
    property_list.new_with(
        :cluster, "SELECT COALESCE(abd_kmz_uuid, apd_kmz_uuid) AS kmz_uuid FROM dim_cluster_master_smallworld WHERE cluster_code = ? LIMIT 1",
        :subfeeder, "SELECT COALESCE(abd_kmz_uuid, apd_kmz_uuid) AS kmz_uuid FROM dim_subfeeder_master_smallworld WHERE subfeeder_code = ? LIMIT 1",
        :feeder, "SELECT COALESCE(abd_kmz_uuid, apd_kmz_uuid) AS kmz_uuid FROM dim_feeder_master_smallworld WHERE feeder_code = ? LIMIT 1"
    ),
    _false
)
```

### 2. Database Connection Slot

Add new slot to engine:
```magik
{:db_connection, _unset, :writable}   # PostgreSQL database connection
```

### 3. New Method: `get_kmz_uuid_from_db(infrastructure_type, code)`

**Purpose:** Retrieve KMZ UUID from database based on infrastructure type and code

**Parameters:**
- `infrastructure_type` (string) - "cluster", "subfeeder", or "feeder"
- `code` (string) - The cluster_code, subfeeder_code, or feeder_code

**Returns:**
- `string` - KMZ UUID or empty string if not found

**Logic:**
```magik
_method rwwi_astri_workorder_engine.get_kmz_uuid_from_db(infrastructure_type, code)
    ## Retrieve KMZ UUID from PostgreSQL database
    ##
    ## Parameters:
    ##   infrastructure_type (string) - "cluster", "subfeeder", or "feeder"
    ##   code (string) - Infrastructure code to search
    ##
    ## Returns:
    ##   string - KMZ UUID or empty string if not found

    _try _with cond
        # Validate inputs
        _if code _is _unset _orif code = ""
        _then
            _return ""
        _endif

        # Establish database connection if not already connected
        _if .db_connection _is _unset
        _then
            (is_connect?, .db_connection) << user:rwwi_external_ds_manager.open_connection_for("[POSTGRESQL_ASTRI_DB]")

            _if _not is_connect?
            _then
                write("WARNING: Failed to connect to POSTGRESQL_ASTRI_DB")
                _return ""
            _endif
        _endif

        # Get SQL query for infrastructure type
        _local sql_query << _self.sql_for_kmz_uuid[infrastructure_type.as_symbol()]

        _if sql_query _is _unset
        _then
            write("WARNING: No SQL query defined for infrastructure type:", infrastructure_type)
            _return ""
        _endif

        # Execute query
        _local recs << .db_connection.sql_select(sql_query, code)
        _local rec << recs.get()

        _local kmz_uuid << ""
        _if rec _isnt _unset
        _then
            kmz_uuid << rec.kmz_uuid.default("")
        _endif

        recs.close()

        _return kmz_uuid

    _when error
        write("ERROR retrieving KMZ UUID from database:", cond.report_contents_string)
        _return ""
    _endtry
_endmethod
```

### 4. Update `parse_xml_response()` Method

Modify the method to fetch KMZ UUID from database after parsing XML:

**Current (Line 335):**
```magik
# KMZ UUID will be retrieved from database, not from API
pl[:kmz_uuid] << ""
```

**Updated:**
```magik
# Retrieve KMZ UUID from database based on infrastructure type and code
pl[:kmz_uuid] << _self.get_kmz_uuid_from_db(infrastructure_type, pl[:cluster_code])
```

### 5. Connection Cleanup

Add method to close database connection:

```magik
_method rwwi_astri_workorder_engine.close_db_connection()
    ## Close PostgreSQL database connection

    _try
        _if .db_connection _isnt _unset
        _then
            .db_connection.rollback()
            .db_connection.close()
            .db_connection << _unset
            write("Database connection closed")
        _endif
    _when error
        # Ignore cleanup errors
    _endtry
_endmethod
```

## Integration Points

### 1. Parse XML Response Flow

```
parse_xml_response(xml_string, infrastructure_type)
  ↓
For each work order:
  - Extract code (cluster_code/subfeeder_code/feeder_code)
  - Call get_kmz_uuid_from_db(infrastructure_type, code)
  - Set pl[:kmz_uuid] with result
  ↓
Return rope of work orders with KMZ UUIDs
```

### 2. Connection Lifecycle

- **Open:** On first call to `get_kmz_uuid_from_db()`
- **Reuse:** Connection persists in `.db_connection` slot
- **Close:** Explicitly call `close_db_connection()` when done or on error

### 3. Error Handling

**Connection Failures:**
- Log warning
- Return empty string
- Continue processing (non-blocking)

**Query Failures:**
- Log error
- Return empty string
- Continue with next work order

**Invalid Parameters:**
- Return empty string immediately
- No database query

## Testing Plan

### Test Scenarios

#### 1. Cluster Infrastructure
```magik
# Test with valid cluster code
uuid << engine.get_kmz_uuid_from_db("cluster", "PLB10103")
# Expected: Returns KMZ UUID if found in database

# Test with invalid cluster code
uuid << engine.get_kmz_uuid_from_db("cluster", "INVALID123")
# Expected: Returns empty string
```

#### 2. Subfeeder Infrastructure
```magik
uuid << engine.get_kmz_uuid_from_db("subfeeder", "PLB10102")
# Expected: Returns KMZ UUID if found in database
```

#### 3. Feeder Infrastructure
```magik
uuid << engine.get_kmz_uuid_from_db("feeder", "PLB10101")
# Expected: Returns KMZ UUID if found in database
```

#### 4. Database Connection
```magik
# Test connection establishment
# Note: rwwi_external_ds_manager is in user package
(success?, conn) << user:rwwi_external_ds_manager.open_connection_for("[POSTGRESQL_ASTRI_DB]")
# Expected: success? = _true, conn is not _unset
```

#### 5. Priority Logic
- Record with `abd_kmz_uuid` = "abc123" and `apd_kmz_uuid` = "xyz789"
  - Expected: Returns "abc123"
- Record with `abd_kmz_uuid` = NULL and `apd_kmz_uuid` = "xyz789"
  - Expected: Returns "xyz789"
- Record with both NULL
  - Expected: Returns empty string

## Reference Implementation

### Files Referenced

1. **rwi_pni_to_tenoss.magik** - SQL query pattern reference
   - Location: `C:\Smallworld\rwi_custom_product\modules\rwi_tenoss_integration\source\rwi_pni_to_tenoss.magik`
   - Pattern: `.connect.sql_select(query, param)`
   - Example (Line 284):
     ```magik
     sel << "SELECT STATUS FROM LAST_STATUS_INT WHERE PROJECT_ID=?"
     recs << .connect.sql_select(sel, .proj_id)
     rec << recs.get()
     recs.close()
     ```

2. **Connection Pattern (Line 169)**
   ```magik
   # Note: rwwi_external_ds_manager is in user package, use user: prefix
   (is_connect?, .connect) << user:rwwi_external_ds_manager.open_connection_for("[POSTGRESQL_TENOSS]")
   ```

## Files to Modify

### 1. rwwi_astri_workorder_engine.magik

**Changes:**
- Add slot: `:db_connection`
- Add shared constant: `sql_for_kmz_uuid`
- Add method: `get_kmz_uuid_from_db(infrastructure_type, code)`
- Add method: `close_db_connection()`
- Update method: `parse_xml_response()` - Line 335 to call database

**Estimated Lines:** ~100 lines added

## Performance Considerations

### Query Optimization
- Each query uses `LIMIT 1` to fetch only one result
- WHERE clause on indexed column (code fields should be indexed)
- Single query per work order

### Connection Pooling
- Reuses same connection for multiple queries
- No connection overhead after first query

### Expected Performance
- Database query: ~10-50ms per work order
- For 50 work orders: ~0.5-2.5 seconds total
- Non-blocking: Failures don't stop processing

## Deployment Notes

### Prerequisites
1. PostgreSQL database accessible at `172.17.52.32:5432`
2. Connection `[POSTGRESQL_ASTRI_DB]` configured in rwwi_external_ds_manager
3. Tables exist: `dim_cluster_master_smallworld`, `dim_subfeeder_master_smallworld`, `dim_feeder_master_smallworld`
4. Fields exist: `abd_kmz_uuid`, `apd_kmz_uuid`, code fields

### Configuration Check
```magik
# Verify connection exists
# Note: rwwi_external_ds_manager is in user package
(success?, conn) << user:rwwi_external_ds_manager.open_connection_for("[POSTGRESQL_ASTRI_DB]")
_if success?
_then
    write("Connection successful")
    conn.close()
_else
    write("Connection failed - check configuration")
_endif
```

## Next Steps

1. ✅ Create implementation plan document (this file)
2. ✅ Add shared constant for SQL queries
3. ✅ Add db_connection slot
4. ✅ Implement get_kmz_uuid_from_db() method
5. ✅ Implement close_db_connection() method
6. ✅ Update parse_xml_response() to call database
7. ⏳ Test with sample work order data
8. ⏳ Verify KMZ UUID retrieval for all infrastructure types
9. ⏳ Test error handling (connection failures, missing records)
10. ⏳ Deploy to production

## Notes

- **Non-blocking Design**: Database failures return empty string, allowing processing to continue
- **Connection Reuse**: Single connection reused for all queries in one session
- **Priority Logic**: Uses COALESCE() in SQL for clean fallback
- **Infrastructure Agnostic**: Same method works for cluster, subfeeder, feeder
- **Error Logging**: All errors logged but don't halt processing
- **Empty Code Handling**: Returns empty string immediately if code is missing

---
**Implementation plan created on 2025-11-10**
