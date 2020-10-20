#!/bin/bash

set -eu -o pipefail

ISO_PATH="/mnt/custom-iso/"
ISO_VERSION="CentOS-7-x86_64-Minimal-2003.iso"
CUSTOM_ISO_VERSION="cortx-custom.iso"
ISO_MOUNT_PATH="/mnt/custom-iso/test"
LOCAL_BOOT_PATH="/mnt/custom-iso/bootisoks"
KICKSTART_FILE="./kickstart_centos-7.8.2003.cfg"
CORTX_DEPS_PATH="/mnt/bigstorage/releases/cortx/third-party-deps/"
OS="centos"
OS_VERSION="centos-7.8.2003"

#Mount ISO locally
mkdir -p $ISO_MOUNT_PATH && mount -o loop $ISO_PATH/$ISO_VERSION $ISO_MOUNT_PATH

#create local folder and copy ISO folders
rm -rf $LOCAL_BOOT_PATH && mkdir -p $LOCAL_BOOT_PATH
cp -r "$ISO_MOUNT_PATH"/* $LOCAL_BOOT_PATH

#umount ISO and remove folder
umount $ISO_MOUNT_PATH && rm $ISO_MOUNT_PATH -rf

#set permissions for local folder
chmod -R u+w $LOCAL_BOOT_PATH

#validate kickstart file
ksvalidator $KICKSTART_FILE

#copy kickstart file and update isolinux.cfg
cp $KICKSTART_FILE $LOCAL_BOOT_PATH/isolinux/ks.cfg
sed -i 's/append\ initrd\=initrd.img/append initrd=initrd.img\ ks\=cdrom:\/ks.cfg/' $LOCAL_BOOT_PATH/isolinux/isolinux.cfg

#copy CORTX packages 
find $CORTX_DEPS_PATH/$OS/$OS_VERSION/ -name '*.rpm' ! -path "$CORTX_DEPS_PATH/$OS/$OS_VERSION/lustre/custom/tcp/*" -exec cp -n {} $LOCAL_BOOT_PATH/Packages/. \;
cd $LOCAL_BOOT_PATH/Packages/ && createrepo -dpo .. .

#generate custom iso file
cd $LOCAL_BOOT_PATH && mkisofs -o $ISO_PATH/$CUSTOM_ISO_VERSION -b isolinux.bin -c boot.cat -no-emul-boot -boot-load-size 4 -boot-info-table -V "CORTX CentOS 7.8.2003 x86_64" -R -J -v -T isolinux/. .

#Add md5sum for custom iso
implantisomd5 $ISO_PATH/$CUSTOM_ISO_VERSION
