#!/bin/bash
if [ $# -eq 0 ]
then
  echo "No device provided. Please provide a device need to be partitioned."
  echo "Usage :"
  echo "        $0 /dev/sdx"
  exit
fi

NUM_PARTITIONS=4
PARTITION_SIZE="+12G"

SED_STRING="o"
TAIL="p
w
q
"

NEW_LINE="
"
LETTER_n="n"
LETTER_p="p"
EXTENDED_PART_NUM=4
TGTDEV=$1

SED_STRING="$SED_STRING$NEW_LINE"
for i in $(seq $NUM_PARTITIONS)
do
  if [ $i -lt $EXTENDED_PART_NUM ]
  then
    SED_STRING="$SED_STRING$LETTER_n$NEW_LINE$NEW_LINE$NEW_LINE$NEW_LINE$PARTITION_SIZE$NEW_LINE"
  fi
  if [ $i -eq $EXTENDED_PART_NUM ]
  then
    SED_STRING="$SED_STRING$LETTER_n$NEW_LINE$LETTER_p$NEW_LINE$NEW_LINE$PARTITION_SIZE$NEW_LINE"
  fi
  if [ $i -gt $EXTENDED_PART_NUM ]
  then
    SED_STRING="$SED_STRING$LETTER_n$NEW_LINE$NEW_LINE$PARTITION_SIZE$NEW_LINE"
  fi
done
SED_STRING="$SED_STRING$TAIL"

sed -e 's/\s*\([\+0-9a-zA-Z]*\).*/\1/' << EOF | fdisk ${TGTDEV}
  $SED_STRING
EOF