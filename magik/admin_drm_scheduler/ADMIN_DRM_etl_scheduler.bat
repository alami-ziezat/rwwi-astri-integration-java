SET SW_GIS_ENVIRONMENT_FILE=F:\SW5\PNI_FTTH536\core\config\environment.bat
SET SW_GIS_ALIAS_FILES=F:\SW5\PNI_FTTH536\pni_ftth\config\gis_aliases

SET DRM_RUN_TYPE=etl
SET DRM_SCHEDULER_DIR=F:\SW5\Scheduler\ADMIN_DRM_scheduler\

SET TIMESTAMP=%date:~10,4%%date:~7,2%%date:~4,2%%time:~0,2%%time:~3,2%
SET LOGFILE=drm_etl_%TIMESTAMP%.log
SET LOGDIR=%DRM_SCHEDULER_DIR%logs
SET JOB_SERVER_LOG=%LOGDIR%\%LOGFILE%

CALL %SW_GIS_ENVIRONMENT_FILE%
CALL F:\SW5\PNI_FTTH536\core\bin\x86\runalias.exe dev_ftth_myrep_custom_open -noiteractive -cli  -login "root/"< %DRM_SCHEDULER_DIR%admin_drm_batch.magik >%JOB_SERVER_LOG%
