@echo off
FOR /F %%A IN ('WMIC OS GET LocalDateTime ^| FINDSTR \.') DO @SET B=%%A
SET LOG=%B:~0,14%

for /F "tokens=*" %%A in (../clients.txt) do call pdf-processing-auto.cmd %%A >../temp/%LOG%.%%A.out.txt 2>../temp/%LOG%.%%A.err.txt
