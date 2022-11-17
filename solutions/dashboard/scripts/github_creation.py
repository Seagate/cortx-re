import os

print("----------------- Codacy -----------------")

os.system("kubectl create -f ../config/github/GithubCronjob.yaml")
print("")
