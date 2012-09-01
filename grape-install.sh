#!/bin/sh
set -e 
set -u 

# Check the CLI parameter
if [ $# -eq 0 ]; then 
  echo "Please specifify the pipeline name on the command line "
  exit 1
fi


#
# Define the Grape environment configuration variables
#
PIPENAME=$1   
PIPEID=$PIPENAME\1
PIPEDB=$PIPENAME
PIPEDB2=$PIPENAME\_common
PIPEHOME=$HOME/grape/pipelines/$PIPENAME


# Install required dependencies
yum install -y svn cyrus-sasl-md5 
yum install -y wget R R-devel cmake mysql mysql-devel mysql-server cpan
yum install -y python-setuptools python-setuptools-devel
easy_install virtualenv


# Start and configure DB 
service mysqld start

# The MySQL DB access credential
cat > ~/.my.cnf <<'EOF'
[client]
host=localhost
user=root
password=
EOF

#
# Make grape
#


# Download pipeline sources 
if [ ! -d $HOME/grape ]; then
cd $HOME 
svn co svn://svn.crg.es/big/pipeline.buildout/trunk grape --password pa3121so --username ptommaso --quiet
fi 

  
# Create the databases
mysql -e "create database $PIPEDB; create database $PIPEDB2;"

 
# Create directories structure
mkdir -p $PIPEHOME
cd $PIPEHOME

cp ../Test/bootstrap.py .
touch buildout.cfg
virtualenv --no-site-packages .
./bin/python bootstrap.py
 
mkdir -p ../../accessions/$PIPENAME
mkdir ../../profiles/$PIPENAME

cp ../../accessions/Test/db.cfg ../../accessions/$PIPENAME
cp ../../profiles/Test/db.cfg ../../profiles/$PIPENAME

cat > buildout.cfg <<EOF
[buildout]
extends = ../dependencies.cfg
          ../../accessions/$PIPENAME/db.cfg
          ../../profiles/$PIPENAME/db.cfg
EOF

# Update the file profiles/db.cfg
perl -i -pe "s/PROJECTID(\s*)=.*/PROJECTID\1= ${PIPEID}/" ../../profiles/$PIPENAME/db.cfg
perl -i -pe "s/DB(\s*)=.*/DB\1= ${PIPEDB}/" ../../profiles/$PIPENAME/db.cfg
perl -i -pe "s/COMMONDB(\s*)=.*/COMMONDB\1= ${PIPEDB2}/" ../../profiles/$PIPENAME/db.cfg


# Download the data
mkdir src
wget -q https://s3.amazonaws.com/cbcrg-lab/grape-testdata.zip
unzip grape-testdata.zip -d src

# Warm up
./bin/buildout

# Some fixes 
cd $PIPEHOME/parts/TestRun/bin
ln -s ../../../src/flux/bin/flux flux.sh

if [ ! -e /soft/bin/perl ]; then
mkdir -p /soft/bin
cd /soft/bin 
ln -s `which perl` perl
fi 

#
# RUN 
# 
cd $PIPEHOME/parts/TestRun
./start.sh
./execute.sh


