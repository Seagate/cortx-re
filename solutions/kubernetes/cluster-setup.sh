#!/bin/bash
HOST_FILE=$PWD/hosts


function generate_rsa_key {
rm -rf /root/.ssh/id_rsa
ssh-keygen -b 2048 -t rsa -f /root/.ssh/id_rsa -q -N ""
}

function passwordless_ssh {
local NODE=$1
local USER=$2
local PASS=$3
sshpass -p $PASS ssh-copy-id -o StrictHostKeyChecking=no -i ~/.ssh/id_rsa.pub $USER@$NODE

}

function nodes_setup {
for i in $(cat $HOST_FILE)
do 
local NODE=$(echo $i | awk -F[,] '{print $1}' | cut -d'=' -f2)
local USER=$(echo $i | awk -F[,] '{print $2}' | cut -d'=' -f2)
local PASS=$(echo $i | awk -F[,] '{print $3}' | cut -d'=' -f2)

echo "Running on $NODE"
passwordless_ssh $NODE $USER $PASS
scp cluster-functions.sh $NODE:/var/tmp/
done
}

function setup_cluster {
ALL_NODES=$(cat $HOST_FILE | awk -F[,] '{print $1}' | cut -d'=' -f2)
MASTER_NODE=$(head -1 $HOST_FILE | awk -F[,] '{print $1}' | cut -d'=' -f2)
WORKER_NODES=$(cat $HOST_FILE | grep -v $MASTER_NODE | awk -F[,] '{print $1}' | cut -d'=' -f2)
echo MASTER NODE=$MASTER_NODE
echo WORKER NODES=$WORKER_NODES
for node in $ALL_NODES
do 
ssh -o 'StrictHostKeyChecking=no' $node '/var/tmp/cluster-functions.sh --prepare'
done

ssh -o 'StrictHostKeyChecking=no' $MASTER_NODE '/var/tmp/cluster-functions.sh --master'
sleep 10 #To be replaced with status check
JOIN_COMMAND=$(ssh -o 'StrictHostKeyChecking=no' $MASTER_NODE 'kubeadm token create --print-join-command --description "Token to join worker nodes"')

for worker_node in $WORKER_NODES
do
ssh -o 'StrictHostKeyChecking=no' $worker_node "echo "y" | kubeadm reset && $JOIN_COMMAND" 
done
}

generate_rsa_key
nodes_setup
setup_cluster
