SET SW_GIS_ENVIRONMENT_FILE=F:\SW5\PNI_FTTH536\core\config\environment.bat
SET SW_GIS_ALIAS_FILES=F:\SW5\PNI_FTTH536\pni_ftth\config\gis_aliases

REM Per-type automated ETL job: SUBFEEDER.
REM Loads smallworld.dim_subfeeder_master_smallworld -> drm_etl_scheduler_log,
REM processes subfeeder, then e-mails its own HTML summary. Independent of the
REM cluster / feeder jobs.
SET DRM_RUN_TYPE=etl
SET DRM_ETL_INFRA_TYPE=subfeeder
REM DRM_SCHEDULER_DIR holds this .bat + the stdin caller (admin_drm_batch.magik) + logs.
REM cmail.exe / recipients.txt are located at runtime from the loaded module's
REM resources (admin_drm_scheduler), not from here.
SET DRM_SCHEDULER_DIR=F:\SW5\Scheduler\admin_drm_scheduler\resources\base\bin\

REM ETL processing time window (HH:MM). Processing stops when the window ends;
REM the report is still e-mailed. Default is 22:00 - 09:00. Uncomment to override:
REM SET DRM_ETL_START=22:00
REM SET DRM_ETL_END=09:00

SET TIMESTAMP=%date:~10,4%%date:~7,2%%date:~4,2%%time:~0,2%%time:~3,2%
SET LOGFILE=drm_etl_subfeeder_%TIMESTAMP%.log
SET LOGDIR=%DRM_SCHEDULER_DIR%logs
SET JOB_SERVER_LOG=%LOGDIR%\%LOGFILE%

CALL %SW_GIS_ENVIRONMENT_FILE%
CALL F:\SW5\PNI_FTTH536\core\bin\x86\runalias.exe dev_ftth_myrep_custom_open -noiteractive -cli  -login "root/"< %DRM_SCHEDULER_DIR%admin_drm_batch.magik >%JOB_SERVER_LOG%
