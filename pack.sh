#!/bin/bash
# ---------------------------------------------------------------------------------------------
# This file creates a ZIP with all needed files
# ---------------------------------------------------------------------------------------------

cd todeploy

# Creating last PDFs version
soffice --headless --convert-to pdf:writer_pdf_Export --outdir docs docs/"The_Une_language.odt"
soffice --headless --convert-to pdf:writer_pdf_Export --outdir docs docs/"The_Mingle_Standard_Platform.odt"
soffice --headless --convert-to pdf:writer_pdf_Export --outdir docs docs/"Une_reference_sheet.odg"

#----------------------------------------------------------------------------------------------

if [ -f src ]; then
    rm -r src/*
else
    mkdir src
fi

cp -r ../candi/src/       src/candi
cp -r ../controllers/src/ src/controllers
cp -r ../glue/src/        src/glue
cp -r ../gum/src/         src/gum
cp -r ../lang/src/        src/lang
cp -r ../network/src/     src/network
cp -r ../pcl/src/         src/pcl
cp -r ../stick/src/       src/stick
cp -r ../tape/src/        src/tape

#----------------------------------------------------------------------------------------------

rm examples/*.model

java -cp ./lib/lang.jar:./lib/controllers.jar:./lib/network.jar:./lib/pcl.jar:./lib/candi.jar \
     -javaagent:lib/lang.jar \
     -jar "tape.jar" examples/*.une

#----------------------------------------------------------------------------------------------

today=$(date +%Y-%m-%d)
fileName="mingle-$today.zip"

zip -r $fileName dockers/* docs/*.pdf examples/* include/* lib/* \
                 config.json glue.jar gum.jar stick.jar tape.jar \
                 menu.sh