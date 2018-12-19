set CLASS=net.paypredict.patient.cases.pdf.processing.RequisitionFormsPdfProcessing$Auto

:START
"../jre/bin/java.exe" -Xmx8g -cp "../lib/*" %CLASS% --client:%1
IF ERRORLEVEL 302 GOTO START