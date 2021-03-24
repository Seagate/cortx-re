#!/bin/bash

helpFunction()
{
   echo ""
   echo "Usage: $0 -s source -d destination -r retention"
   echo -e "\t-s Remote build path"
   echo -e "\t-d Local build path"
   echo -e "\t-r Retention peroid for sync"
   exit 1 # Exit script after printing help
}

while getopts "s:d:r:" opt
do
   case "$opt" in
      s ) source="$OPTARG" ;;
      d ) destination="$OPTARG" ;;
      r ) retention="$OPTARG" ;;
      ? ) helpFunction ;; # Print helpFunction in case parameter is non-existent
   esac
done

# Print helpFunction in case parameters are empty
if [ -z "$source" ] || [ -z "$destination" ] || [ -z "$retention" ]
then
   echo "Some or all of the parameters are empty";
   helpFunction
fi

if [ ! -d "$source" ]; then
   echo "Error: Remote path '$source' is NOT found."
   exit 1
fi

if [ ! -d "$destination" ]; then
   echo "Warning: Local path '$destination' is NOT found."
   echo "Creating the local path '$destination'..."
   mkdir -p "$destination"
fi

SRCDIR=$source
DESTDIR=$destination
NUMDAYS=$retention

pushd "$SRCDIR"
echo "searching for files to sync"
#only find artifacts that are NUMDAYS old
folder_list=$(find . -mindepth 1 -maxdepth 1 -type d -mtime -"$NUMDAYS" -printf "%f\n")

echo "Folders List: $folder_list"

for i in "${folder_list[@]}"
do
  echo "syncing folder $i, this may take a while"
  rsync -av --ignore-existing ./"$i" "$DESTDIR"
  rsync -av --update ./"$i" "$DESTDIR" --delete
done

#rsync --files-from=$TMPFILE $SRCDIR 744417@10.230.242.73:$DESTDIR
