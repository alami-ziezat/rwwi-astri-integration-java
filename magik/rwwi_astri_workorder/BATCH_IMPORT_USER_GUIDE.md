# Batch Import — User Guide (ASTRI Work Order dialog)

**Audience:** operators (root / admin) who need to load many work orders at once and either
migrate them into Smallworld immediately or queue them for the scheduled run.

**Where:** the **Batch Import** toolbar inside the **ASTRI Work Order** dialog.

---

## 1. What Batch Import does

Instead of loading work orders one filter at a time, Batch Import lets you:

1. Point at a plain-text **list of infra codes** (a `.txt` file), grouped by infrastructure type.
2. **Load** all the matching work orders into the dialog table in one action.
3. **Process** every loaded work order in one of two ways:
   - **Directly** — migrate them into Smallworld now, and e-mail a summary report; **or**
   - **via Scheduler** — queue them so the nightly scheduled run migrates them later.

It always works across the three infrastructure types in the fixed order
**FEEDER → SUBFEEDER → CLUSTER**.

---

## 2. Who can use it

The **Batch Import toolbar is visible only to `root` and `admin` users.** If you are logged in
as a normal user, the toolbar is hidden and none of this applies to you. (This is decided from
your GIS login name at the time the dialog opens.)

---

## 3. Open the dialog

From the application menu/action, choose **"ASTRI Work Orders…"**. The ASTRI Work Order dialog
opens. Find the row labelled **`Batch Import:`** — that is the toolbar used throughout this guide.

The Batch Import toolbar has six controls, left to right:

```
Batch Import:  [📁]  [ ...path... ]  [🔍]  [✖]     Process:  [ mode ▾ ]  [▶]
                Browse   path field   Load   Clear             dropdown   Process
```

Hover any button to see its tooltip. Each control is explained in detail below
([Section 3a](#3a-each-control-in-detail)).

### 3a. Each control in detail

**① Browse** — *folder icon* · tooltip: "Browse for infra_code list (.txt file)"
- **Purpose:** choose the `.txt` file that lists the infra codes you want to load.
- **Usage:** click it → a file picker opens (in your `TEMP` folder by default, or the last folder
  you used) → select a `.txt` file → its full path appears in the path field.
- **Effect:** selecting a file **enables Load WO List and Clear**.
- **Always enabled.**

**② Path field** — *read-only text box*
- **Purpose:** shows the path of the file you selected with Browse.
- **Usage:** display only — you don't type here; use Browse to change it, or Clear to empty it.

**③ Load WO List** — *magnifying-glass icon* · tooltip: "Load WO List - fetch and display work
orders for all infra_codes in the file"
- **Purpose:** read the file and pull in every matching work order.
- **Usage:** click after a file is selected. The dialog parses the file into sections, then fetches
  the WOs **one code at a time** from the ASTRI API, filling the table. Watch the **footer** for live
  progress (`Fetching [cluster] 3/10: JKT00003`) and the **log** for `Section [cluster]: matched 8 / 10`
  and any `Not found` codes. The **Donation** column auto-fills where applicable (feeder skipped).
- **Effect:** if at least one WO is found, **Process enables**; if none are found, Process stays
  greyed.
- **Enabled:** only after a file is selected (greyed at start).

**④ Clear** — *clear (✖) icon* · tooltip: "Clear batch import list"
- **Purpose:** reset the Batch Import toolbar to start over.
- **Usage:** click to empty the path field, forget the loaded sections, and **disable Load WO List
  and Process**. Then Browse for a different file.
- **Enabled:** only after a file is selected (greyed at start).

**⑤ Process mode** — *dropdown* (default **Import All APD to SW Directly**)
- **Purpose:** choose **what** the Process button does. Two options:
  - **Import All APD to SW Directly** — migrate every displayed WO into Smallworld **now**.
  - **Import All APD to SW via Scheduler** — **queue** them for the scheduled run (no migration now).
- **Usage:** set this **before** clicking Process. See [Section 6](#6-the-two-process-modes-in-detail)
  for exactly what each does.

**⑥ Process** — *run (▶) icon* · tooltip: "Process - run the selected action on all displayed work
orders"
- **Purpose:** execute the selected mode on **all** WOs currently shown in the table.
- **Usage:** click after Load WO List has found WOs and you've picked a mode. Read the confirmation
  and summary popups and the log. **Don't close the dialog** while a *Directly* migration is running.
- **Enabled:** only after Load WO List loads at least one WO (greyed at start and after Clear).

---

## 4. Prepare the code-list `.txt` file

The file is a plain-text list of infra codes grouped under **bracketed section headers**.

### Format

```
[FEEDER]
BDG001511
BDG001402
[SUBFEEDER]
BBS000366
[CLUSTER]
JKT00001
JKT00002
```

### Rules

- A **section header** starts with `[` and ends with `]`. The word inside (case-insensitive)
  must be **`FEEDER`**, **`SUBFEEDER`**, or **`CLUSTER`**.
- Under each header, put **one infra code per line**.
- **Blank lines are ignored.**
- You may include **1, 2, or 3 sections, in any order.** Only the types you list are loaded.
- **A code before any header, or an unknown header word, is an error** — the load is refused
  with a message and nothing is fetched. Fix the file and try again.
- The **same code may appear under two different types** (e.g. `PLB006435` under both
  `[SUBFEEDER]` and `[CLUSTER]`) — both are kept as separate rows.

### Size limit

- **Maximum 25 codes per type** (so at most **75 codes total**).
- If any type exceeds 25, the load is blocked with a popup listing the offending types and their
  counts. Reduce the list and try again.

Tip: save the file somewhere easy to find (e.g. your `TEMP` folder) — the Browse dialog opens
there by default.

---

## 5. Step-by-step

### Step 1 — Browse for the file
Click **Browse** (folder icon). Pick your `.txt` file. The path appears in the field, and
**Load WO List** and **Clear** become enabled.

### Step 2 — Load the work orders
Click **Load WO List** (magnifying-glass icon). The dialog:

- Parses the file into sections.
- Fetches work orders **one code at a time** from the ASTRI API (each code is looked up by its
  server-side code filter). The **footer shows live progress**, e.g. `Fetching [cluster] 3/10: JKT00003`.
- Fills the table with every matched work order. Each row shows its **Infra Type**.
- Auto-fills the **Donation** column where applicable (feeder rows are skipped — donation doesn't
  apply to feeder).

Watch the **log panel**. For each section you'll see a line like `Section [cluster]: matched 8 / 10`,
and any code with no match is logged `Not found [cluster]: JKT00099`. A single bad/failed code is
skipped (`Fetch error … - skipped`) — it never aborts the rest of the batch.

When loading finishes, the footer summarises: `Batch load complete. Found 22 / 25 WO(s).`

> **If nothing was found**, the **Process** button stays disabled — check the log for "Not found"
> lines and verify the codes and types in your file.

### Step 3 — Choose what to do (Process mode)
Set the **Process mode** dropdown to one of:

| Mode | Effect |
|---|---|
| **Import All APD to SW Directly** *(default)* | Migrate every displayed WO into Smallworld **now**. |
| **Import All APD to SW via Scheduler** | **Queue** every displayed WO for the **scheduled** run (no migration now). |

See [Section 6](#6-the-two-process-modes-in-detail) for exactly what each does.

### Step 4 — Process
Click **Process** (run icon). Read the confirmation/summary popups and the log panel
(Section 6 explains the numbers). Do **not** close the dialog while a direct migration is running.

### Clearing / starting over
Click **Clear** at any time to drop the file selection and disable Load/Process. Then Browse for a
different file.

---

## 6. The two Process modes in detail

### 6a. "Import All APD to SW Directly" (default)

Migrates every displayed work order **immediately** — no scheduler involved.

- Order: **FEEDER → SUBFEEDER → CLUSTER**.
- For each WO it creates the Smallworld project + design from the APD KMZ.
- When done it writes an **HTML summary report** and **e-mails it** to the configured recipients.
- You get a summary popup:

  ```
  Migrate All complete.
    Total processed  : 22
    Total successful : 20
    Total failed     : 2

  Summary report e-mailed.
  ```

Use this when you want the data in Smallworld **right away** and you are watching the run.

### 6b. "Import All APD to SW via Scheduler"

Does **not** migrate now. It **queues** every displayed WO into the scheduler table
`smallworld.drm_scheduler_logs` (status `scheduled`), to be migrated later by the **manual
scheduled run** (`ADMIN_DRM_scheduler.bat` / `admin_drm_scheduler.run_and_email(:manual)`).

Before queuing each WO it applies two safety checks and **skips** (never aborts) when:

1. **A Smallworld project/design already exists** for that WO — skipped.
2. **It is already in the scheduler queue** (same infra_code + infra_type) — skipped.

You get a summary popup:

```
Add All complete.
  Added to scheduler              : 18
  Skipped (design/project exists) : 3
  Skipped (already scheduled)     : 1
```

Use this to build up an overnight workload. **Nothing is migrated until the scheduled DRM run
executes.** See the scheduler module's guide:
`../admin_drm_scheduler/README.md` (Section A — the **Manual DRM** job).

> **Note:** this queue (`drm_scheduler_logs`) is the **manual** pipeline. It is separate from the
> **automated ETL** pipeline (`drm_etl_scheduler_log`), which is now fed automatically from the
> `dim_*_master_smallworld` tables — not from this button.

---

## 7. Button states (quick reference)

| After… | Browse | Load WO List | Clear | Process |
|---|---|---|---|---|
| Dialog opens | enabled | disabled | disabled | disabled |
| You pick a file (Browse) | enabled | **enabled** | **enabled** | disabled |
| Load WO List, WOs **found** | enabled | enabled | enabled | **enabled** |
| Load WO List, **none** found | enabled | enabled | enabled | disabled |
| Clear | enabled | disabled | disabled | disabled |

---

## 8. Reading the results

- **Footer** (bottom of the dialog): live progress during loading, then the final `Found X / N`.
- **Log panel**: per-section match counts, "Not found" codes, per-WO "Added/Skipped/Failed"
  (Scheduler mode) or the migration summary (Direct mode).
- **Popup**: the final summary (counts) after Process.
- **E-mail** (Direct mode): the HTML summary report is mailed to the recipients configured in the
  scheduler module (`admin_drm_scheduler/resources/base/data/recipients.txt`).

---

## 9. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| I don't see the Batch Import toolbar | You are not logged in as `root`/`admin`. The toolbar is hidden for normal users. |
| "Could not parse file…" | A code appears before any `[TYPE]` header, or a header word isn't FEEDER/SUBFEEDER/CLUSTER. Fix the file format (Section 4). |
| "Batch list exceeds the allowed limit" | A type has more than 25 codes (max 75 total). Reduce the list. |
| Load runs but Process stays greyed out | No work orders matched. Check the log's "Not found" lines and confirm codes/types. |
| Some codes show "Not found" | The API returned no work order for that code under that type. Verify the code and that it's under the correct `[TYPE]` header. |
| Scheduler mode shows lots of "Skipped" | Expected — those WOs already have a design, or are already queued. Only genuinely new WOs are added. |
| Direct mode: "Summary report written (e-mail not sent)" | The migration ran but the e-mail step failed — check SMTP/recipients in the `admin_drm_scheduler` module. |

---

## 10. Related

- **Scheduler runs** (manual DRM + the 3 automated ETL jobs, and how "via Scheduler" WOs get
  migrated): `../admin_drm_scheduler/README.md`.
- **ETL auto-source design** (why the ETL queue is no longer fed from the UI):
  `../../docs/etl_auto_source_from_dim_tables_plan.md`.
