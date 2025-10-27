# RWI ASTRI Integration - Java Source

This directory contains the Java source code for the RWI ASTRI Integration module.

## Directory: rwwi_astri_integration_java

**Previous Name:** `java_src_v2` (renamed on 2025-10-27)

## Structure

```
rwwi_astri_integration_java/
├── pom.xml                           # Maven build configuration
├── README.md                         # This file
└── src/main/java/com/rwi/myrepublic/astri/
    ├── AstriConfig.java              # Configuration singleton
    ├── AstriWorkOrderProcs.java      # Work Order APIs (2)
    ├── AstriWorkOrderUpdateProcs.java # Work Order Update API
    ├── AstriPriceListProcs.java      # Price List API
    ├── AstriKmzDownloadProcs.java    # KMZ Download APIs (4)
    ├── AstriVendorProcs.java         # Vendor API
    ├── AstriBoqProcs.java            # BOQ DRM API
    ├── AstriOltProcs.java            # OLT Rollout API
    └── internal/
        ├── WorkOrderClient.java      # Internal HTTP client
        ├── WorkOrderUpdateClient.java # Internal HTTP client
        ├── PriceListClient.java      # Internal HTTP client
        ├── KmzDownloadClient.java    # Internal HTTP client
        ├── VendorClient.java         # Internal HTTP client
        ├── BoqClient.java            # Internal HTTP client
        └── OltClient.java            # Internal HTTP client
```

**Total:** 15 Java files (8 @MagikProc classes + 7 internal clients + AstriConfig)

## Building

To build the JAR file:

```bash
cd C:\Smallworld\pni_custom\rwwi_astri_integration_java
mvn clean package
```

This will create `pni_custom.rwwi.astri.integration.1.jar` in the `../libs` directory.

## APIs Implemented

### Work Order APIs (3)
1. `astri_get_work_orders(limit, offset, _optional filters)` - List work orders
2. `astri_get_work_order(uuid)` - Get single work order
3. `astri_update_work_order(number, latest_status_name, detail)` - Update work order

### Price List API (1)
4. `astri_get_price_list(_optional filters)` - Get price list

### KMZ Download APIs (4)
5. `astri_download_cluster_kmz(uuid, _optional output_dir)` - Download cluster KMZ
6. `astri_download_subfeeder_kmz(uuid, _optional output_dir)` - Download subfeeder KMZ
7. `astri_download_feeder_kmz(uuid, _optional output_dir)` - Download feeder KMZ
8. `astri_download_olt_site_kmz(uuid, _optional output_dir)` - Download OLT site KMZ

### Vendor API (1)
9. `astri_get_vendor_list(limit, offset, _optional filters)` - List vendors

### BOQ DRM API (1)
10. `astri_add_boq_drm_cluster(...)` - Add BOQ DRM cluster (13 parameters)

### OLT Rollout API (1)
11. `astri_get_olt_list(limit, offset, _optional filters)` - List OLT devices

**Total:** 12 Magik procedures exposed via @MagikProc annotations

## Requirements

- Java 17
- Maven 3.x
- Smallworld GIS 5.3.6 (for Magik interop JARs)

## Documentation

See `RWI_ASTRI_INTEGRATION_PLAN.md` in this directory for complete documentation including:
- Complete API Reference for all 12 APIs
- Architecture and design patterns
- Implementation details
- Testing procedures
- Change log and version history

## Module

This Java code is packaged as an OSGi bundle and loaded by the Magik module:
- Module: `rwwi_astri_integration`
- Location: `C:\Smallworld\pni_custom\modules\rwwi_astri_integration`
