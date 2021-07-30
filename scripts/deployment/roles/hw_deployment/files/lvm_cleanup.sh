#!/usr/bin/sh

log_file="/var/log/seagate/motr/mini_provisioner"
vol_grps=$(vgs|grep vg_srvnode|awk '{print $1}')
vgs_arr=($vol_grps)
vgs_count=( ${#vgs_arr[@]} )
# Function to execute command and log output to file
exec_cmd()
{
     echo "$(date):: Executing $1" 2>&1 | tee -a $log_file
     $1 2>&1 | tee -a $log_file
}

# Create log file if not exists
if test -f "$log_file"
then
    echo "$log_file already exists"
else
    echo "Creating $log_file"
    touch $log_file
fi
echo "This script is to remove MOTR created LVMs. It may take some time. Logs can be found in $log_file"
echo "This script is to remove MOTR created LVMs." 2>&1 | tee -a $log_file
# If no MOTR LVs are present, exit
if [ $vgs_count -eq 0 ]
then
    echo "No VG found"  2>&1 | tee -a $log_file
    exit 0
fi
echo "$(date) Total VGs : $vgs_count" 2>&1 | tee -a $log_file
# Execute swapoff and remove entries from /etc/fstab
exec_cmd "swapoff -a"
echo "$(date):: Removing LVM entries from /etc/fstab" 2>&1 | tee -a $log_file
sed -i.bak '/vg_srvnode/d' /etc/fstab
echo "$(date):: Total VGs = ${vgs_arr[@]}" 2>&1 | tee -a $log_file
for (( i=0; i<$vgs_count; i++))
do
    vg_name=${vgs_arr[$i]}
    pv_name=$(pvs|grep $vg_name|awk '{print $1}')
    lv_names=$(lvs|grep $vg_name|awk '{print $1}')
    lvs_arr=($lv_names)
    lvs_count=( ${#lvs_arr[@]} )
    echo "====Following VG, LV, PV are to be removed====" 2>&1 | tee -a $log_file
    echo "$(date):: VG  = $vg_name" 2>&1 | tee -a $log_file
    echo "$(date):: LVs  = ${lvs_arr[@]}" 2>&1 | tee -a $log_file
    echo "$(date):: PV  = $pv_name" 2>&1 | tee -a $log_file
    for (( j=0; j<$lvs_count; j++))
    do
        lv_name=${lvs_arr[$j]}
        lv_path="/dev/$vg_name/$lv_name"
        exec_cmd "lvchange -an $lv_path"
        exec_cmd "lvremove $lv_path"
        exec_cmd "sleep 5"
    done
    exec_cmd "vgchange -an $vg_name"
    exec_cmd "vgremove $vg_name"
    exec_cmd "sleep 5"
    exec_cmd "pvremove $pv_name"
    exec_cmd "sleep 5"
done
