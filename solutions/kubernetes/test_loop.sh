HOST_FILE=$PWD/hosts
pushd cortx-k8s/k8_cortx_cloud/
yq eval '.solution.nodes[].name' solution.yaml

 for worker_node in $(sed '1d' "$HOST_FILE")
    do
        NODE=$(echo "$worker_node" | awk -F[,] '{print $1}' | cut -d'=' -f2)
        echo $NODE

    done
popd
