from cortxportscanner.common.const import HEADLESS_SERVICE_NAME, STATEFULSET_NAME, STATEFULSET_VOLUME_NAME, SECRET_NAME
from kubernetes import client


class StatefulSet:
    def __init__(self, client: client, apps_api: client.AppsV1Api, specs) -> None:
        self.client = client
        self.apps_api = apps_api
        self.specs = specs

    def handle_statefulset(self):
        self.create_statefulset()
        self.check_statefulset_status()

    def create_statefulset(self):
        statefulset_body = self.create_statefulset_body()

        try:
            self.apps_api.create_namespaced_stateful_set(
                namespace=self.specs['namespace'], body=statefulset_body)
            print("\n\nStatefulset Created")
        except Exception as err:
            print("\n\nStatefulset Exception: \n", err)

    def create_statefulset_body(self):
        # Container Template
        mongo_container = self.client.V1Container(
            name=STATEFULSET_NAME,
            image="mongo:latest",
            image_pull_policy="Always",
            ports=[self.client.V1ContainerPort(container_port=27017)],
            env=[
                self.client.V1EnvVar(
                    name="MONGO_INITDB_ROOT_USERNAME",
                    value_from=self.client.V1EnvVarSource(
                        secret_key_ref=self.client.V1SecretKeySelector(
                            name=SECRET_NAME, key="mongodb_username"
                        )
                    )
                ),
                self.client.V1EnvVar(
                    name="MONGO_INITDB_ROOT_PASSWORD",
                    value_from=self.client.V1EnvVarSource(
                        secret_key_ref=self.client.V1SecretKeySelector(
                            name=SECRET_NAME, key="mongodb_password"
                        )
                    )
                ),
            ],
            volume_mounts=[
                self.client.V1VolumeMount(
                    name=STATEFULSET_VOLUME_NAME,
                    mount_path="/data/db"
                )
            ],
        )

        # Pod Template
        template = self.client.V1PodTemplateSpec(
            metadata=self.client.V1ObjectMeta(
                labels={"name": STATEFULSET_NAME},
                namespace=self.specs['namespace']
            ),
            spec=self.client.V1PodSpec(
                containers=[mongo_container],
                termination_grace_period_seconds=10,
            )
        )

        # Claim name is 'data' because after creating PVC it will suffixed by pod name
        volume_claim_tamplate = self.client.V1PersistentVolumeClaim(
            api_version='v1',
            kind='PersistentVolumeClaim',
            metadata=self.client.V1ObjectMeta(
                name=STATEFULSET_VOLUME_NAME
            ),
            spec=self.client.V1PersistentVolumeClaimSpec(
                storage_class_name="local-path",
                access_modes=["ReadWriteOnce"],
                resources=self.client.V1ResourceRequirements(
                    requests={
                        "storage": "1Gi"
                    }
                )
            )
        )

        # Spec
        spec = self.client.V1StatefulSetSpec(
            replicas=1,
            service_name=HEADLESS_SERVICE_NAME,
            selector=self.client.V1LabelSelector(
                match_labels={"name": STATEFULSET_NAME}
            ),
            template=template,
            volume_claim_templates=[volume_claim_tamplate]
        )

        # StatefulSet
        body = self.client.V1StatefulSet(
            api_version="apps/v1",
            kind="StatefulSet",
            metadata=self.client.V1ObjectMeta(
                name=STATEFULSET_NAME, namespace=self.specs['namespace']),
            spec=spec
        )

        return body

    def check_statefulset_status(self):
        resp = None

        try:
            print("Checking Replicas Status: ")
            resp = self.read_statefulset()
            print("Replicas: {}/{}".format(resp.status.replicas, resp.spec.replicas))

            # While will run until all the replicas are not created
            while resp.status.replicas != resp.spec.replicas:
                resp = self.read_statefulset()
                # print("Replicas: {}/{}".format(resp.status.replicas, resp.spec.replicas))
            print("Replicas: {}/{}".format(resp.status.replicas, resp.spec.replicas))

            print("Checking Ready Replicas: ")
            ready_replicas_cnt = 0 if resp.status.ready_replicas is None else resp.status.ready_replicas
            print("Replicas: {}/{}".format(ready_replicas_cnt, resp.spec.replicas))

            # While will run until all the replicas are not ready
            while resp.status.ready_replicas != resp.spec.replicas:
                resp = self.read_statefulset()

                ready_replicas_cnt = 0 if resp.status.ready_replicas is None else resp.status.ready_replicas
                # print("Replicas: {}/{}".format(ready_replicas_cnt, resp.spec.replicas))

            ready_replicas_cnt = 0 if resp.status.ready_replicas is None else resp.status.ready_replicas
            print("Replicas: {}/{}".format(ready_replicas_cnt, resp.spec.replicas))

            print("Statefulset Created Successfully!")

        except Exception as err:
            print("Statefulset Status Check Exception: ", err)

    def read_statefulset(self):
        resp = self.apps_api.read_namespaced_stateful_set(
            name=STATEFULSET_NAME, namespace=self.specs['namespace'])
        return resp
