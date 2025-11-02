# RWWI ASTRI Integration - Folder Structure

## Overview
This document describes the reorganized folder structure for the RWWI ASTRI Integration project.

## Reorganization Date
2025-10-30

## New Folder Structure

```
C:\Smallworld\pni_custom\rwwi_astri_integration_java\
│
├── src\                           # Java source code
│   └── main\
│       └── java\
│           └── com\
│               └── rwi\
│                   └── myrepublic\
│                       └── astri\
│                           ├── AstriConfig.java
│                           ├── AstriBoqProcs.java
│                           ├── AstriKmzDownloadProcs.java
│                           ├── AstriOltProcs.java
│                           ├── AstriPriceListProcs.java
│                           ├── AstriVendorProcs.java
│                           ├── AstriWorkOrderProcs.java
│                           ├── AstriWorkOrderUpdateProcs.java
│                           └── internal\
│                               ├── BoqClient.java
│                               ├── KmzDownloadClient.java
│                               ├── OltClient.java
│                               ├── PriceListClient.java
│                               ├── VendorClient.java
│                               ├── WorkOrderClient.java
│                               └── WorkOrderUpdateClient.java
│
├── magik\                         # Magik modules directory
│   ├── rwwi_astri_integration\   # ASTRI integration base module
│   │   ├── load_list.txt
│   │   ├── module.def
│   │   ├── resources\
│   │   │   └── astri_config.properties
│   │   └── source\
│   │       ├── astri_kml_parser.magik
│   │       ├── load_list.txt
│   │       └── test_astri_procs.magik
│   │
│   └── rwwi_astri_workorder\     # Work Order Manager module
│       ├── load_list.txt
│       ├── module.def
│       ├── README.md
│       ├── DEMO_MODE_IMPLEMENTATION.md
│       ├── FILTER_DEBUG_TEST.md
│       ├── FILTER_IMPLEMENTATION.md
│       ├── FILTER_SOLUTION_FINAL.md
│       ├── IMPLEMENTATION_PLAN.md
│       ├── JAVA_STRING_FIX.md
│       ├── JAVA_TO_MAGIK_STRING_ALL_APIS.md
│       ├── KMZ_DUAL_SCENARIO_IMPLEMENTATION.md
│       ├── KMZ_UUID_IMPLEMENTATION.md
│       ├── MAGIK_INTEROP_UTILS_INVESTIGATION.md
│       ├── resources\
│       │   └── en_gb\
│       │       └── messages\
│       │           ├── rwwi_astri_workorder_dialog.msg
│       │           ├── rwwi_astri_workorder_dialog.msgc
│       │           ├── rwwi_astri_workorder_plugin.msg
│       │           └── rwwi_astri_workorder_plugin.msgc
│       └── source\
│           ├── conditions.magik
│           ├── load_list.txt
│           ├── rwwi_astri_workorder_dialog.magik
│           ├── rwwi_astri_workorder_engine.magik
│           └── rwwi_astri_workorder_plugin.magik
│
├── libs\                          # Generated JAR files
│   └── pni_custom.rwwi.astri.integration.1.jar
│
├── pom.xml                        # Maven build configuration
├── README.md                      # Project documentation
└── FOLDER_STRUCTURE.md           # This file
```

## Module Descriptions

### 1. rwwi_astri_integration
**Location:** `magik\rwwi_astri_integration\`

**Purpose:** Core ASTRI integration module providing:
- Java interop bridge via @MagikProc annotations
- Base API client functionality
- KML parsing utilities
- Configuration management

**Key Files:**
- `module.def` - Module definition
- `astri_config.properties` - API configuration
- `astri_kml_parser.magik` - KML/KMZ parsing utilities
- `test_astri_procs.magik` - Test procedures for Java calls

**Java Integration:**
- All @MagikProc annotated methods in `src\main\java\com\rwi\myrepublic\astri\`
- Compiled into JAR: `pni_custom.rwwi.astri.integration.1.jar`

### 2. rwwi_astri_workorder
**Location:** `magik\rwwi_astri_workorder\`

**Purpose:** Work Order Manager UI module providing:
- Work order retrieval and display
- KMZ download functionality (dual scenario)
- Migration to Design/Temporary objects
- Filter and search capabilities

**Key Files:**
- `rwwi_astri_workorder_plugin.magik` - Plugin registration
- `rwwi_astri_workorder_dialog.magik` - UI dialog implementation
- `rwwi_astri_workorder_engine.magik` - Data access layer
- `conditions.magik` - Error condition definitions

**Dependencies:**
- Requires `rwwi_astri_integration` module
- Uses Java integration via global @MagikProc procedures

## Changes Made

### Previous Structure
```
magik\
├── load_list.txt
├── module.def
├── resources\
└── source\
```

### New Structure
```
magik\
├── rwwi_astri_integration\      # ← MOVED HERE (renamed from magik/)
│   ├── load_list.txt
│   ├── module.def
│   ├── resources\
│   └── source\
│
└── rwwi_astri_workorder\        # ← COPIED HERE
    ├── load_list.txt
    ├── module.def
    ├── resources\
    └── source\
```

### Rationale

1. **Clearer Module Organization:**
   - `rwwi_astri_integration` clearly identifies the base integration module
   - Both modules are now at the same level under `magik\`

2. **Better Code Reusability:**
   - Work order module can be loaded independently
   - Base integration can be used by other modules

3. **Consistent Naming:**
   - Folder name matches module name
   - Easier to understand module relationships

4. **Centralized Location:**
   - All Magik modules in one place
   - Easier deployment and maintenance

## Module Loading Order

When loading in Smallworld, the order should be:

1. **rwwi_astri_integration** (base module)
   - Provides @MagikProc global procedures
   - Provides KML parsing utilities

2. **rwwi_astri_workorder** (dependent module)
   - Uses procedures from rwwi_astri_integration
   - Provides UI and workflow

## Build Process

The Maven build process (`mvn clean package`) will:

1. Compile Java source files from `src\main\java\`
2. Generate JAR file: `libs\pni_custom.rwwi.astri.integration.1.jar`
3. Include Magik modules from `magik\` directory (if configured)

The JAR must be deployed to Smallworld's Java classpath for the Magik modules to function correctly.

## Deployment

### For Development:
```bash
# Build Java
cd C:\Smallworld\pni_custom\rwwi_astri_integration_java
mvn clean package

# Deploy JAR to Smallworld
copy libs\pni_custom.rwwi.astri.integration.1.jar <smallworld_install>\libs\

# Load Magik modules in session
load_module("rwwi_astri_integration")
load_module("rwwi_astri_workorder")
```

### For Production:
1. Copy entire `magik\rwwi_astri_integration\` to product modules directory
2. Copy entire `magik\rwwi_astri_workorder\` to product modules directory
3. Deploy JAR to Smallworld Java classpath
4. Configure `astri_config.properties` with production settings
5. Load modules via product definition

## Related Modules

### Original Location (Still Exists):
- `C:\Smallworld\pni_custom\modules\rwwi_astri_workorder\`
  - This is the **original** source location
  - `magik\rwwi_astri_workorder\` is a **copy** for deployment

### Development Workflow:
1. Edit source files in `C:\Smallworld\pni_custom\modules\rwwi_astri_workorder\`
2. Copy to `magik\rwwi_astri_workorder\` when ready to deploy
3. Build and test

## Environment Variables

The modules may use the following environment variables:
- `ASTRI_DEMO_MODE` - Enable/disable demo mode (default: true)
- `TEMP` - Temporary directory for file downloads
- `ASTRI_CONFIG_PATH` - Override default config location

## Notes

1. **Backup:** Original files are preserved in `C:\Smallworld\pni_custom\modules\`
2. **Synchronization:** Changes to the original should be synchronized to `magik\` copy
3. **JAR Dependency:** Both Magik modules require the compiled JAR to function
4. **Module Order:** Always load `rwwi_astri_integration` before `rwwi_astri_workorder`

## Future Enhancements

1. **Automated Sync:**
   - Create script to sync changes from modules\ to magik\
   - Add to build process

2. **Product Definition:**
   - Create formal product definition file
   - Include both modules with proper dependencies

3. **Configuration Management:**
   - Separate dev/test/prod configurations
   - Environment-specific property files
