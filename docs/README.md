# RWWI ASTRI Work Order Manager

**Version:** 1.0
**Created:** 2025-10-28
**Status:** ✅ IMPLEMENTED

## Overview

Smallworld GIS UI module for managing ASTRI Work Orders from both API and database sources.

## Features

- ✅ Display work orders in sortable table with 9 columns
- ✅ Toggle between API and Database data sources
- ✅ Comprehensive filtering (category, status, topology, cluster code)
- ✅ Pagination controls (limit/offset)
- ✅ Work order details panel with selection
- ✅ Download KMZ files for selected work orders
- ✅ Mark work orders as "under construction"
- ✅ Real-time data refresh

## Module Structure

```
rwwi_astri_workorder/
├── module.def                           # Module definition
├── load_list.txt                        # Load order
├── README.md                            # This file
├── IMPLEMENTATION_PLAN.md               # Detailed implementation plan
├── source/
│   ├── load_list.txt
│   ├── conditions.magik                 # Custom error conditions
│   ├── rwwi_astri_workorder_engine.magik   # Data access layer (~380 lines)
│   ├── rwwi_astri_workorder_dialog.magik   # UI layer (~520 lines)
│   └── rwwi_astri_workorder_plugin.magik   # Plugin layer (~50 lines)
└── resources/
    └── en_gb/
        └── messages/
            ├── rwwi_astri_workorder_plugin.msg
            └── rwwi_astri_workorder_dialog.msg
```

## Dependencies

- **base** - Smallworld core module
- **rwwi_astri_integration** - ASTRI API Java integration module

## Loading the Module

The module is registered in `pni_custom` module and will load automatically.

To load manually in Magik session:
```magik
sw_module_manager.load_module(:rwwi_astri_workorder)
```

## Activating the UI

### From Plugin Action
The plugin registers an action `:activate_workorder` with caption "ASTRI Work Orders..."

### From Code
```magik
smallworld_product.applications[:rwwi_astri_workorder_plugin].activate_workorder()
```

## Testing

### Test Engine
```magik
# Create engine instance
engine << rwwi_astri_workorder_engine.new()

# Test API call
workorders << engine.get_workorders_from_api(10, 0)
write("Retrieved", workorders.size, "work orders")

# Test DB query
db_workorders << engine.get_workorders_from_db()
write("Found", db_workorders.size, "construction work orders")
```

## Database Schema Requirements

The module expects a `work_order` collection in the `:design_admin` dataset with these fields:

- `uuid` (string) - Unique identifier
- `number` (string) - Work order number
- `target_cluster_code` (string) - Cluster code
- `target_cluster_name` (string) - Cluster name
- `category_label` (string) - Category display label
- `latest_status_name` (string) - Status
- `target_cluster_topology` (string) - Topology (AE/UG/OH)
- `assigned_vendor_label` (string) - Vendor name
- `created_at` (timestamp) - Creation timestamp
- `updated_at` (timestamp) - Update timestamp
- **`construction_status` (boolean)** - NEW FIELD for marking records

### Add the New Field

```sql
ALTER TABLE work_order
ADD COLUMN construction_status BOOLEAN DEFAULT FALSE;

CREATE INDEX idx_work_order_construction_status
ON work_order(construction_status)
WHERE construction_status = TRUE;
```

## API Integration

The module uses these global procedures from `rwwi_astri_integration`:

- `astri_get_work_orders(limit, offset, filters)` - Get work orders
- `astri_download_kmz_cluster(cluster_code)` - Download KMZ file

### API Field Mapping

| API JSON Field | Property List Key | Table Column |
|----------------|-------------------|--------------|
| `uuid` | `:uuid` | (internal) |
| `number` | `:wo_number` | WO Number |
| `target_cluster_code` | `:cluster_code` | Cluster Code |
| `target_cluster_name` | `:cluster_name` | Cluster Name |
| `category_label` | `:category` | Category |
| `latest_status_name` | `:status` | Status |
| `target_cluster_topology` | `:topology` | Topology |
| `assigned_vendor_label` | `:vendor` | Vendor |
| `created_at` | `:created_at` | Created Date |

## UI Components

Built using Smallworld standard components:
- `rowcol` - Layout containers
- `panel` - Bordered panels
- `tree_item` - Multi-column table
- `text_choice_item` - Dropdown selectors
- `text_item` - Text input fields
- `sw_button` - Action buttons
- `label_item` - Static labels

## Known Limitations

1. **JSON Parsing** - Uses basic string parsing. Should be replaced with proper JSON parser (e.g., `simple_xml` or `json_parser`) for production use.
2. **User Dialogs** - Uses simple output messages. Should be enhanced with proper modal dialogs.
3. **Error Handling** - Basic error handling. Should be enhanced with user-friendly error dialogs.

## Future Enhancements

- [ ] Implement proper JSON parser
- [ ] Add export to CSV/Excel
- [ ] Add bulk operations (mark multiple as construction)
- [ ] Add work order history tracking
- [ ] Add auto-refresh option
- [ ] Add advanced search
- [ ] Improve modal dialog support

## References

- Implementation Plan: `IMPLEMENTATION_PLAN.md`
- UI Component Examples: `C:\Smallworld\core\sw_core\modules\sw_swift\magik_gui_components_examples`
- Reference Module: `C:\Smallworld\rwi_custom_product\modules\rwi_library_name`

## Support

For issues or questions, contact the development team.

---

**Last Updated:** 2025-10-28
**Module Author:** Claude Code
**License:** Internal Use Only
