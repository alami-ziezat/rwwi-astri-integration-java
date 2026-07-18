SET SW_GIS_ENVIRONMENT_FILE=C:\Smallworld\core\config\environment.bat
SET SW_GIS_ALIAS_FILES=C:\Smallworld\pni_ftth\config\gis_aliases

REM Per-type automated ETL job: FEEDER.
REM Loads smallworld.dim_feeder_master_smallworld -> drm_etl_scheduler_log,
REM processes feeder, then e-mails its own HTML summary. Independent of the
REM cluster / subfeeder jobs.
SET DRM_RUN_TYPE=etl
SET DRM_ETL_INFRA_TYPE=feeder
REM DRM_SCHEDULER_DIR holds this .bat + the stdin caller (admin_drm_batch.magik) + logs.
REM cmail.exe / recipients.txt are located at runtime from the loaded module's
REM resources (admin_drm_scheduler), not from here.
SET DRM_SCHEDULER_DIR=C:\Smallworld\pni_custom\rwwi_astri_integration_java\magik\admin_drm_scheduler\resources\base\bin

REM ETL processing time window (HH:MM). Processing stops when the window ends;
REM the report is still e-mailed. Default is 22:00 - 09:00. Uncomment to override:
REM SET DRM_ETL_START=21:40
REM SET DRM_ETL_END=22:50

SET TIMESTAMP=%date:~10,4%%date:~7,2%%date:~4,2%%time:~0,2%%time:~3,2%
SET LOGFILE=drm_etl_feeder_%TIMESTAMP%.log
SET LOGDIR=%DRM_SCHEDULER_DIR%logs
SET JOB_SERVER_LOG=%LOGDIR%\%LOGFILE%

CALL %SW_GIS_ENVIRONMENT_FILE%
CALL C:\Smallworld\core\bin\x86\runalias.exe ftth_custom_open_myrep -noiteractive -cli  -login "root/"< %DRM_SCHEDULER_DIR%admin_drm_batch.magik >%JOB_SERVER_LOG%
