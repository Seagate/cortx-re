mkdir -p /mnt/custom-iso/test
mount -o loop /mnt/custom-iso/CentOS-7-x86_64-Minimal-2003.iso /mnt/custom-iso/test
rm -rf /mnt/custom-iso/bootisoks && mkdir /mnt/custom-iso/bootisoks
cp -r /mnt/custom-iso/test/* /mnt/custom-iso/bootisoks/
umount /mnt/custom-iso/test/ && rm /mnt/custom-iso/test/ -rf
chmod -R u+w /mnt/custom-iso/bootisoks/
cp /root/kickstart_centos-7.8.2003.cfg /mnt/custom-iso/bootisoks/isolinux/ks.cfg
sed -i 's/append\ initrd\=initrd.img/append initrd=initrd.img\ ks\=cdrom:\/ks.cfg/' /mnt/custom-iso/bootisoks/isolinux/isolinux.cfg
cd /mnt/custom-iso/bootisoks/ && mkisofs -o /mnt/custom-iso/custom-boot.iso -b isolinux.bin -c boot.cat -no-emul-boot -boot-load-size 4 -boot-info-table -V "CentOS 7 x86_64" -R -J -v -T isolinux/. .
implantisomd5 /mnt/custom-iso/custom-boot.iso
