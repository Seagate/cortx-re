#!/usr/bin/env groovy

// Path where the jobs get created.
job_path="Release_Engineering/github-work"

// Jenkinsfile path for pipeline project
jenkinsfile_path = "${new File(__FILE__).parent}/jenkinsfile"

//  job name : jenkinsfile path
def job_map = [
  "CSM"               : "csm.groovy",
  "Provisioner"       : "provsioner.groovy",
  "Mero"              : "mero.groovy",
  "S3Server"          : "s3server.groovy",
  "Hare"              : "hare.groovy",
  "NFS"               : "nfs.groovy",
  "SSPL"              : "sspl.groovy",
  "Cortx-HA"          : "ha.groovy",
  "GitHub Release"    : "release.groovy",
]

// 1 - Create Folder
absolute_folder_path=""
for (String folder_path: job_path.split("/")) {
  
  absolute_folder_path=(absolute_folder_path+"/"+folder_path).replaceAll("^/+", "");
  
  folder("${absolute_folder_path}") {
    description("Folder containing all ${folder_path} jobs for cortx components")
  }  
}

// 2 - Create Jobs - Iterate over map to get job informations
for (job in job_map) {

  // DSL syntax to create piepline job    Ref : https://jenkinsci.github.io/job-dsl-plugin/#path/pipelineJob
  pipelineJob("${job_path}/${job.key}") {
    description("This job is created by Jenkins groovy DSL script. Don't change the config manually, Instead follow the Git workflow")
    definition {
      cps {
        script(readFileFromWorkspace("${jenkinsfile_path}/${job.value}"))
        sandbox()     
      }
    }
  }
}
