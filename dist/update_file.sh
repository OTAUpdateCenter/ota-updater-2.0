#!/bin/bash
# Copyright (C) 2014 OTA Update Center
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

API_URL="https://otaupdatecenter.pro/api/v1"
SC_VER="0.9.0"

help=0
if [[ $# == 0 ]] ; then
  help=1
fi

opttmp=`getopt -o hu:t:f:v:d:z:m:c:x:l:k: --long \
help,user:,type:file_id:,version:,otadate:,\
file:,md5:,changelog:,changelogfile:,\
device:,url:,key:verbose -- "$@"`

if [[ $? != 0 ]] ; then echo "INPUT ERROR! Terminating" >&2 ; exit 1 ; fi

eval set -- "$opttmp"

USER=-1
TYPE=-1
FILE_ID=-1
VERSION=-1
OTADATE=-1
file=-1
MD5=-1
CHANGELOG=-1
changelog_file=-1
verbose=0
URL=-1
KEY="$HOME/.ssh/id_rsa"

while true; do
  case "$1" in
    -u | --user )          USER="$2";           shift 2 ;;
    -t | --type )          TYPE="$2";           shift 2 ;;
    -f | --file_id )       FILE_ID="$2";        shift 2 ;;
    -v | --version )       VERSION="$2";        shift 2 ;;
    -d | --otadate )       OTADATE="$2";        shift 2 ;;
    -z | --file )          file="$2";           shift 2 ;;
    -m | --md5 )           MD5="$2";            shift 2 ;;
    -c | --changelog )     CHANGELOG="$2";      shift 2 ;;
    -x | --changelogfile ) changelog_file="$2"; shift 2 ;;
    -l | --url )           URL="$2";            shift 2 ;;
    -k | --key )           KEY="$2";            shift 2 ;;
    --verbose )            verbose=1;           shift   ;;

    -h | --help )          help=1;              shift ; break ;;

    -- ) shift; break ;;
    * ) break ;;
  esac
done

if [[ $help == 1 ]] ; then
  echo
  echo "+=================================================================+"
  echo "|       *** OTA Update Center - Auto Update Script HELP ***       |"
  echo "+=================================================================+"
  echo "| Usage: update_rominfo.sh [-h|--help]  -  Print this             |"
  echo "|        update_rominfo.sh {-u|--user} <username>                 |"
  echo "|                          {-t|--type} {rom|kernel}               |"
  echo "|                          {-f|--file_id} <file id>               |"
  echo "|                          {-l|--url} <download URL>              |"
  echo "|                          {-z <update.zip file>|-m <md5>}        |"
  echo "|                          [{-t|--otadate} <ota date/time>]       |"
  echo "|                          [{-v|--version} <ota version>]         |"
  echo "|                          [{-c <changelog>|-x <changelogfile>}]  |"
  echo "|                          [{-k|--key} <key file>]                |"
  echo "|                          [--verbose]                            |"
  echo "+-----------------------------------------------------------------+"
  echo "| <user>          - your username or email                        |"
  echo "| <file id>       - File ID of the ROM or Kernel File to update   |"
  echo "|                   found on edit page for that file on the site  |"
  echo "| <download URL>  - URL where the update.zip is hosted            |"
  echo "| <.zip file>     - update.zip file to use for getting MD5-sum    |"
  echo "| <md5>           - specify MD5 instead of calculating            |"
  echo "| <ota date/time> - date/time of update, yyyymmdd-hhmm format     |"
  echo "|                   if unspecified, current date/time is used     |"
  echo "| <ota version>   - version of ota update                         |"
  echo "|                   if unspecified, current version is used       |"
  echo "| <changelog>     - changelog for update (be sure to quote)       |"
  echo "| <changelog file>- file to read changelog from                   |"
  echo "|                   if unspecified, blank changelog is used if    |"
  echo "|                   updating version, current changelog otherwise |"
  echo "| <key file>      - private key file to use                       |"
  echo "|                   if unspecified, ~/.ssh/id_rsa is used         |"
  echo "+=================================================================+"
  exit 0
fi

if [[ $USER_ID == -1 ]] ; then
  echo "User ID not specified! Terminating" >&2
  exit 2
fi

if [[ "$TYPE" != "rom" && "$TYPE" != "kernel" ]] ; then
  if [[ $TYPE == -1 ]] ; then
    echo "Type not specified! Terminating" >&2
  else
    echo "Invalid type specified! Terminating" >&2
  fi
  exit 3
fi

if [[ $FILE_ID == -1 ]] ; then
  echo "ROM File ID not specified! Terminating" >&2
  exit 4
fi

if [[ $URL == -1 ]] ; then
  echo "Download URL not specified! Terminating" >&2
  exit 5
fi

if [[ $file == -1 && $MD5 == -1 ]] ; then
  echo "Neither file path nor file md5 specified! Terminating" >&2
  exit 6
elif [[ $file != -1 && $MD5 != -1 && $verbose == 1 ]]; then
  echo "WARNING: both file and MD5 specified, computed MD5 will be used"
fi

if [[ $file != -1 ]] ; then
  echo -n "Computing MD5 ... "
  MD5=`md5sum "$file" | awk '{ print $1 }'`
  echo "DONE: $MD5"
fi

if [[ $VERSION == -1 ]] ; then
  VERSION=""
  if [[ $verbose == 1 ]] ; then
    echo "WARNING: version not specified, will not update version!"
  fi
fi

if [[ $OTADATE == -1 ]] ; then
  OTADATE=`date +%Y%m%d-%k%M`
  if [[ $verbose == 1 ]] ; then
    echo "WARNING: ota time not specified, using $OTADATE"
  fi
fi

if [[ $CHANGELOG == -1 && $changelog_file == -1 ]] ; then
  CHANGELOG=""
  if [[ $verbose == 1 ]] ; then
    echo "WARNING: changelog not specified, using blank changelog"
  fi
elif [[ $CHANGELOG != -1 && $changelog_file != -1 ]] ; then
  echo "WARNING: both changelog text and file specified, file will be used"
fi

if [[ $changelog_file != -1 ]] ; then
  CHANGELOG=`cat "$changelog_file"`
fi

VERSION=`echo -n "$VERSION" | tr '"' '\"'`
URL=`echo -n "$URL" | tr '"' '\"'`
CHANGELOG=`echo -n "$CHANGELOG" | tr '"' '\"'`

payload="{
    \"version\":   \"$VERSION\",
    \"date\":      \"$OTADATE\",
    \"url\":       \"$URL\",
    \"md5\":       \"$MD5\",
    \"changelog\": \"$CHANGELOG\"
}"
#payload='asdftest'

sig=`echo -n "$payload" | \
     openssl dgst -sha1 -sign "$KEY" | \
     openssl enc -base64 | \
     tr -d '\n'`

curl -v \
    -X POST \
    --data "$payload" \
    -H "Content-Type: application/json" \
    -H "X-API-Username: $USER" \
    -H "X-API-Signature: $sig" \
    -A "OTA Update Center Upload Script v$SC_VER" \
    "$API_URL/${TYPE}s/files/$FILE_ID"

echo
echo "DONE"
