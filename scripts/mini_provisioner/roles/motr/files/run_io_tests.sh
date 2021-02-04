dd if=/dev/urandom of=/tmp/128M bs=1M count=128
/opt/seagate/cortx/hare/libexec/m0crate-io-conf > /tmp/m0crate-io.yaml
m0crate -S /tmp/m0crate-io.yaml

if [ $? -eq 0 ]
then
    echo "IO Test : PASSED"
else
    echo "IO Test : FAILED"
fi