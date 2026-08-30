# Plan: Separate the Stella API caller into its own project (`rwwi_stella_integration_java`)

**Date:** 2026-08-30 (plan) / updated 2026-08-30 (implemented, including a follow-up Magik module rename — see §8)
**Status:** Implemented. §§1-7 below are the original plan as written/approved; §8 records what was actually done, including one change beyond the original plan (the Magik dialog module was ultimately moved and renamed too, not left in place as §3/§5 step 7 originally said).

## 0. Context

The "Stella" document-upload API caller was added to the ASTRI integration jar as a small, one-off feature (see `docs/homepass_upload_stella_api_plan_2026-08-15.md`), following the existing per-feature class-pair convention of that project (`AstriXProcs.java` + `internal/XClient.java`). It has since become clear this is its own external system (own host, own API family) piggy-backing on ASTRI's jar/config purely for convenience, and should be split out — the same way NISA already was (`rwwi_nisa_integration_java`, a sibling Maven+Magik project living inside `pni_custom/rwwi_astri_integration_java/`).

This doc plans that separation. **No code is moved/renamed yet** — this is the design to review before executing.

## 1. What exists today

### 1.1 Java layer (inside the `astri` project)

All under `pni_custom/rwwi_astri_integration_java/src/main/java/com/rwi/myrepublic/astri/`, compiled into the `rwwi.astri.integration` OSGi bundle (`pni_custom.rwwi.astri.integration.1.jar`):

| File | Role |
|---|---|
| `AstriStellaDocumentUploadProcs.java` | `@MagikProc(@Name("astri_upload_stella_document"))` — exposes `astri_upload_stella_document(file_path, cluster_code, doc_type, _optional file_name)` to Magik |
| `internal/StellaDocumentUploadClient.java` | HTTP client — POSTs `{cluster_code, file_name, file_base64}` JSON to `AstriConfig.getStellaBaseUrl() + "/osp/cluster/document/homepass-database/stella/upload"`, Basic Auth, returns a normalized XML envelope |

**Coupling point:** `StellaDocumentUploadClient` calls `AstriConfig.getInstance()` for its base URL, username/password, and timeouts — the same shared singleton every other ASTRI client uses (`AstriConfig.java`). It already has its own dedicated config key, though, distinct from the rest of ASTRI:

```properties
# astri_config.properties
astri.stella.base.url=http://172.17.52.160/astri-api-v2/v4   # different host from astri.api.base.url / astri.dm.base.url (172.17.75.22)
astri.username=smallworld                                     # shared credentials, reused as-is today
astri.password=Smallworld@2025!
```

So Stella is already logically a separate external system (different host) — it's only sharing Java packaging and Basic Auth credentials with ASTRI out of convenience, not necessity.

### 1.2 Magik layer (caller side, stays where it is — see §5)

`pni_custom/rwwi_astri_integration_java/magik/rwi_stela_integration/source/rwi_stela_integration_dialog.magik` ("Stela Integration" design-QA/reporting dialog — unrelated in purpose to Stella beyond this one upload feature):

- Line 3100: `_local xml_result << astri_upload_stella_document(path, cluster_code, doc_type, file_name)` — the **only** call site of the Java-backed proc.
- Line 2027 (`_global datalake_stella_by_cluster_code`) / called at line 2292 — a **separate, pure-Magik** global proc that opens its own direct JDBC connection to `POSTGRESQL_ASTRI_DB` and runs `SELECT olt_code FROM smallworld.dim_cluster_stella_master_smallworld WHERE cluster_code_astri = ? LIMIT 1`. This is also "Stella"-branded data but is plain Magik, not part of the Java jar — **out of scope for this plan** unless you want it moved too (see §6 open question).
- `module.def` for `rwi_stela_integration` currently has **no `requires` block at all** (still true today, despite `docs/homepass_upload_stella_api_plan_2026-08-15.md` §2.7 recommending one) — it only works because `rwwi_astri_workorder` (which does require `rwwi_astri_integration`) happens to load earlier in `pni_custom`'s module list, pulling the OSGi bundle in transitively.

### 1.3 Product wiring

`pni_custom/modules/pni_custom/module.def` requires block (the actual top-level "load these" list) already includes both `rwwi_nisa_integration` and `rwi_stela_integration` (lines 26-27). A new Stella Magik module, if one is added, needs a line here too.

## 2. Precedent to mirror: how NISA was separated

`rwwi_nisa_integration_java/` is a fully independent Maven + Magik project nested inside `rwwi_astri_integration_java/` as a sibling folder. Exact shape:

```
rwwi_astri_integration_java/
└── rwwi_nisa_integration_java/
    ├── pom.xml                              # own artifactId, own jar output
    ├── src/main/java/com/rwi/myrepublic/nisa/
    │   ├── NisaConfig.java                  # OWN config singleton, not AstriConfig
    │   ├── NisaFatLossProcs.java            # @MagikProc(@Name("nisa_fat_loss_detection")) — "nisa_" prefix, not "astri_nisa_"
    │   ├── NisaMassProblemProcs.java
    │   └── internal/*Client.java
    ├── magik/rwwi_nisa_integration/
    │   ├── module.def                       # requires_java: rwwi.nisa.integration
    │   ├── load_list.txt
    │   ├── resources/nisa_config.properties # OWN properties file
    │   └── source/*.magik
    └── docs/*.md
```

Key details worth copying exactly:
- `pom.xml` → `<outputDirectory>../libs</outputDirectory>` (resolves to `rwwi_astri_integration_java/libs/`, the Maven staging area — **not** the real deployed classpath; someone still manually copies the built jar to `pni_custom/libs/`, same as the astri jar).
- `finalName`: `pni_custom.rwwi.nisa.integration.1` → jar `Bundle-SymbolicName: rwwi.nisa.integration`, `Export-Package: com.rwi.myrepublic.nisa`.
- Own config properties file copied into the jar via the `maven-resources-plugin` block, not shared with `astri_config.properties`.
- Magik `module.def` declares `requires_java: rwwi.nisa.integration` explicitly (the astri equivalent, `rwwi_astri_workorder/module.def`, only has `requires: rwwi_astri_integration` — no explicit `requires_java`; NISA's pattern is the more correct/explicit one worth following for the new module).
- Global procs are named for the new project (`nisa_*`), not the old one (`astri_nisa_*`).

## 3. Target structure for Stella

```
rwwi_astri_integration_java/
└── rwwi_stella_integration_java/            # NEW — sibling of rwwi_nisa_integration_java
    ├── pom.xml                              # artifactId: stella-integration-v1
    ├── src/main/java/com/rwi/myrepublic/stella/
    │   ├── StellaConfig.java                # NEW — own config singleton
    │   ├── StellaDocumentUploadProcs.java   # moved+renamed from AstriStellaDocumentUploadProcs.java
    │   └── internal/
    │       └── StellaDocumentUploadClient.java  # moved, package changed to com.rwi.myrepublic.stella.internal
    ├── magik/rwwi_stella_integration/
    │   ├── module.def                       # requires_java: rwwi.stella.integration
    │   ├── load_list.txt
    │   ├── resources/stella_config.properties  # NEW
    │   └── source/                          # empty or a thin test proc file, mirroring test_nisa_procs.magik
    └── docs/
        └── (this file moves here once the project exists)
```

No new Magik *dialog* module — `rwi_stela_integration` (the design-QA dialog that happens to call the upload proc) stays exactly where it is; it just calls a renamed global proc (§5).

## 4. Naming decisions

| Item | Old (astri) | New (stella) |
|---|---|---|
| Java package | `com.rwi.myrepublic.astri` | `com.rwi.myrepublic.stella` |
| Procs class | `AstriStellaDocumentUploadProcs` | `StellaDocumentUploadProcs` |
| Client class | `internal.StellaDocumentUploadClient` | `internal.StellaDocumentUploadClient` (package changes, name doesn't need to) |
| Magik global proc | `astri_upload_stella_document(...)` | `stella_upload_document(...)` (drop the redundant "astri" + "stella" double-branding, matches NISA's bare `nisa_*` style) |
| Config class | `AstriConfig.getStellaBaseUrl()` | `StellaConfig.getBaseUrl()` (own singleton, own properties file) |
| Maven artifactId | (part of `astri-integration-v2`) | `stella-integration-v1` |
| Jar output | (part of `pni_custom.rwwi.astri.integration.1.jar`) | `pni_custom.rwwi.stella.integration.1.jar` |
| Bundle-SymbolicName | (part of `rwwi.astri.integration`) | `rwwi.stella.integration` |
| Export-Package | (part of `com.rwi.myrepublic.astri`) | `com.rwi.myrepublic.stella` |
| Magik module | (call site lives in `rwi_stela_integration`) | new `rwwi_stella_integration` module for `requires_java` wiring; `rwi_stela_integration` keeps depending on it |

Renaming the Magik global proc means **one call site to update** (`rwi_stela_integration_dialog.magik:3100`) — low risk, already identified.

## 5. Migration steps

1. **Scaffold the new project** — copy the NISA folder shape (§2) under `rwwi_stella_integration_java/`.
2. **Move + adapt Java files:**
   - `AstriStellaDocumentUploadProcs.java` → `rwwi_stella_integration_java/src/main/java/com/rwi/myrepublic/stella/StellaDocumentUploadProcs.java`, package declaration updated, `@Name` changed to `"stella_upload_document"`.
   - `internal/StellaDocumentUploadClient.java` → same tree, package `com.rwi.myrepublic.stella.internal`, `import com.rwi.myrepublic.astri.AstriConfig` replaced with a new `com.rwi.myrepublic.stella.StellaConfig`.
   - Delete both files from the old `astri` package tree.
3. **New `StellaConfig.java`** — copy `AstriConfig`'s singleton pattern, trimmed to what Stella needs: `getBaseUrl()`, `getUsername()`, `getPassword()`, `getRequestTimeout()`, `getConnectionTimeout()`, loading `stella_config.properties`. Seed values from the current `astri.stella.*` keys:
   ```properties
   stella.api.base.url=http://172.17.52.160/astri-api-v2/v4
   stella.username=smallworld
   stella.password=Smallworld@2025!
   stella.timeout.request=30000
   stella.timeout.connection=10000
   ```
   (Confirm with the user/API owner whether Stella genuinely uses the *same* credentials as ASTRI long-term, or whether that's coincidental — worth flagging, not assuming.)
4. **Remove the now-redundant `astri.stella.base.url` key** from `astri_config.properties` once nothing in the `astri` package references `getStellaBaseUrl()` any more (grep confirmed `StellaDocumentUploadClient` is its only caller).
5. **New `pom.xml`** for the Stella project, cloned from NISA's (§2), with `finalName: pni_custom.rwwi.stella.integration.1`, `Bundle-SymbolicName: rwwi.stella.integration`, `Export-Package: com.rwi.myrepublic.stella`.
6. **New Magik module `rwwi_stella_integration`** (`magik/rwwi_stella_integration/`) — `module.def` with `requires: base` and `requires_java: rwwi.stella.integration`, empty/minimal `source/` (a thin smoke-test proc file, mirroring `test_nisa_procs.magik`, is optional but matches convention).
7. **Update the one caller:** `rwi_stela_integration_dialog.magik:3100` — `astri_upload_stella_document(...)` → `stella_upload_document(...)`.
8. **Fix `rwi_stela_integration/module.def`** while touching it anyway — add the still-missing `requires` block (`base`, `rwwi_stella_integration` now, instead of the transitive `rwwi_astri_integration` dependency it never declared).
9. **Wire into the product:** add `rwwi_stella_integration` to `pni_custom/modules/pni_custom/module.def`'s requires list (needs to load before `rwi_stela_integration`, same as `rwwi_astri_integration` does today for `rwwi_astri_workorder`).
10. **Build + deploy:**
    - `mvn package` in both `rwwi_astri_integration_java/` (now smaller — Stella classes removed) and the new `rwwi_stella_integration_java/`.
    - Copy both rebuilt jars to `pni_custom/libs/` (the real deployed classpath — confirmed with you earlier this session, not `core/libs/`).
    - Remove the now-stale `pni_custom.rwwi.astri.integration.1.jar` if the version number/filename doesn't change automatically (it won't — same `finalName` — a straight overwrite is correct here since Stella classes are simply no longer packaged into it).
    - Restart `gis.exe` so both OSGi bundles reload cleanly.
11. **Verify:** open the Stela Integration dialog, run the existing upload flow (manual and auto-upload, per `docs/homepass_upload_stella_api_plan_2026-08-15.md` §4) end-to-end against the new `stella_upload_document` proc.

## 6. Open questions for you to confirm before I execute

1. **`datalake_stella_by_cluster_code`** (§1.2) — pure-Magik, queries `dim_cluster_stella_master_smallworld` directly. Move it into the new `rwwi_stella_integration` Magik module too (for a genuinely clean separation), or leave it in `rwi_stela_integration_dialog.magik` since it's plain SQL with no Java/jar dependency either way?
2. **Credentials** — okay to have `StellaConfig` start with the *same* username/password currently in `astri_config.properties`, or should Stella get its own credentials from whoever owns that API?
3. **Proc rename** — confirm `stella_upload_document(...)` (dropping the `astri_upload_` prefix) is fine, vs. keeping the exact old name `astri_upload_stella_document` for now to minimize diff/risk (only one call site either way, so low cost to rename).
4. Should the old, now-empty Stella slots in `astri_config.properties` (`astri.stella.base.url`) be deleted immediately, or left as a harmless leftover for one release cycle in case something else still reads it?

## 7. Rollback

Since nothing is deleted until the new project builds cleanly, rollback is just: don't wire `rwwi_stella_integration` into `pni_custom/modules/pni_custom/module.def`, don't remove the two files from the `astri` package, and don't redeploy. Low risk overall — one call site, one proc, no data migration involved.

## 8. Implementation record

### 8.1 §6 open questions — answers received

1. **`datalake_stella_by_cluster_code`** — left in place, pure-Magik, not moved. (It now lives in `rwwi_stella_integration_dialog.magik`, having moved along with the whole dialog file per §8.2 below, but it was not otherwise touched.)
2. **Credentials** — confirmed intentional: Stella uses the same username/password as ASTRI, only the host differs. `StellaConfig`/`stella_config.properties` were seeded with the values from §5 step 3 as planned.
3. **Proc rename** — confirmed: `stella_upload_document(...)`, dropping the `astri_upload_` prefix, as planned.
4. **Old `astri_config.properties` key** — removed immediately (not left for a release cycle): `astri.stella.base.url` deleted along with `AstriConfig.getStellaBaseUrl()`, since nothing in the `astri` package referenced either any more.

§§1-7's Java-layer steps (1-6, 10 partially) were executed as written: `StellaConfig.java`, `StellaDocumentUploadProcs.java`, `internal/StellaDocumentUploadClient.java` created under `rwwi_stella_integration_java/src/main/java/com/rwi/myrepublic/stella/`; the two old files deleted from `astri`; both `pom.xml`s built clean (`mvn package`); both jars copied to `pni_custom/libs/` (confirmed as the real deployed classpath, not `core/libs/` — the old duplicate `pni_custom.rwi.astri.integration.1.jar`, unrelated stale bundle found during this work, was removed separately). `gis.exe` restarted and the upload flow was confirmed working end-to-end by the user before §8.2 below.

### 8.2 Beyond the original plan: the Magik dialog module was moved and renamed too

After testing confirmed the Java-layer split worked, a follow-up request asked to also move the Magik dialog module itself — `rwi_stela_integration` (note the pre-existing "rwi_"/single-L "stela" spelling) — into the new project and rename it to match, superseding §3's "No new Magik *dialog* module... stays exactly where it is" and §5 step 7's "just call a renamed global proc in place."

**What moved**, from `pni_custom/rwwi_astri_integration_java/magik/rwi_stela_integration/` to `pni_custom/rwwi_astri_integration_java/rwwi_stella_integration_java/magik/rwwi_stella_integration/` (merged into the module already scaffolded in §5 step 6, rather than living as a separate module — one module now covers both the `requires_java` wiring and the dialog/plugin/engine logic):

| Old | New |
|---|---|
| `source/rwi_stela_integration_dialog.magik` | `source/rwwi_stella_integration_dialog.magik` |
| `source/rwi_stela_integration_engine.magik` | `source/rwwi_stella_integration_engine.magik` |
| `source/rwi_stela_integration_plugin.magik` | `source/rwwi_stella_integration_plugin.magik` |
| `resources/*.xlsx` (BOQ export templates) | same files, same flat `resources/` layout (unchanged — these are read via hardcoded absolute paths today, not module resource lookup, so relocating them doesn't change runtime behavior; see the pre-existing hardcoded-path issue noted in `stela_integration_module_assessment_2026-08-15.md` §5.3) |

Every occurrence of the identifier `rwi_stela_integration` (exemplar names `rwi_stela_integration_dialog`/`_engine`/`_plugin`, all their methods, the module name itself) was substituted to `rwwi_stella_integration` throughout, plus human-readable "Stela Integration"/"Stela" text (button caption, comments) to "Stella Integration"/"Stella". Verified with a clean repo-wide grep afterward — no live-code references to the old name remain (only the two dated, pre-existing docs from 2026-08-15 still describe the old name, as historical snapshots of that point in time — not updated, since they document what was true then).

`module.def` was merged (not left as two separate modules, to avoid a Smallworld duplicate-module-name situation now that both would be called `rwwi_stella_integration`):

```
rwwi_stella_integration	1

description
	Stella Integration - FTTH design-QA summary/reporting dialog (Cluster/FAT/
	Homepass counts and validity checks) with Excel BOQ export and Stella
	document upload. Carries the rwwi.stella.integration Java OSGi bundle
	(document upload API caller).
end

requires
	base
end

requires_java
	rwwi.stella.integration
end
```

`source/load_list.txt` now lists `test_stella_procs`, `rwwi_stella_integration_dialog`, `rwwi_stella_integration_engine`, `rwwi_stella_integration_plugin` (same relative dialog→engine→plugin order as the original, test file first).

`pni_custom/modules/pni_custom/module.def`'s requires list now has a single `rwwi_stella_integration` line (the separate `rwi_stela_integration` line was removed — the module no longer exists under that name).

The old `magik/rwi_stela_integration/` directory was deleted entirely after everything was copied and verified. One pre-existing, unrelated leftover was noticed but intentionally not touched: `magik/rwi_stela_integration_20260815.zip`, a dated backup archive sitting alongside the module folders — not part of the loaded product either way.

**Known pre-existing issue carried over, not introduced by this rename:** `rwwi_stella_integration_plugin.magik`'s `activate_rwwi_stella_integration()` caches the dialog under `:rwwi_stella_integration` but looks it up under `:stella_integration` (`get_dialog(:stella_integration)` vs `cache_dialog(:rwwi_stella_integration, d)`) — a symbol mismatch that means the cache lookup never hits and a new dialog instance is created on every activation. This mismatch already existed before the rename (`:stela_integration` vs `:rwi_stela_integration` in the original file) and was preserved as-is since fixing it wasn't part of what was asked — flagging here in case it's worth a follow-up.

**Verification still needed:** `mvn package` was already re-run for the Java layer in the prior step (§8.1) and is unaffected by this Magik-only rename. This step itself needs `gis.exe` restarted (module load order changed) and the Stela/Stella Integration dialog manually re-tested — action, dialog open, BOQ export, and the upload flow (already working pre-rename per §8.1) should all be re-confirmed post-rename.
