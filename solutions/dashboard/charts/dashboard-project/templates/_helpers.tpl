{{/*
Defining resources names
*/}}

{{- define "dashboard-project.tools.dashboard_secret_name" -}}
{{- default "dashboard-secret" .Values.tools.dashboard_secret_name | trunc 63 | trimSuffix "-" }}
{{- end}}

{{- define "dashboard-project.tools.dashboard_configmap_name" -}}
{{- default "dashboard-configmap" .Values.tools.dashboard_configmap_name | trunc 63 | trimSuffix "-" }}
{{- end}}

{{- define "dashboard-project.codacy.name" -}}
{{- default "dashboard-codacy" .Values.codacy.name | trunc 63 | trimSuffix "-" }}
{{- end}}

{{- define "dashboard-project.github.name" -}}
{{- default "dashboard-github" .Values.github.name | trunc 63 | trimSuffix "-" }}
{{- end}}

{{- define "dashboard-project.jenkins.name" -}}
{{- default "dashboard-jenkins" .Values.jenkins.name | trunc 63 | trimSuffix "-" }}
{{- end}}

{{- define "dashboard-project.logstash_configmap.name" -}}
{{- default "dashboard-logstash-configmap" .Values.logstash_configmap.name | trunc 63 | trimSuffix "-" }}
{{- end}}

{{- define "dashboard-project.logstash.name" -}}
{{- default "dashboard-logstash" .Values.logstash.name | trunc 63 | trimSuffix "-" }}
{{- end}}

{{- define "dashboard-project.mongodb.name" -}}
{{- default "dashboard-mongodb" .Values.mongodb.name | trunc 63 | trimSuffix "-" }}
{{- end}}

{{- define "dashboard-project.port-scanner.name" -}}
{{- default "dashboard-port-scanner" .Values.portscanner.name | trunc 63 | trimSuffix "-" }}
{{- end}}

{{- define "dashboard-project.port-scanner.service-account-name" -}}
{{- default "dashboard-port-scanner" .Values.portscanner.serviceAccount.name | trunc 63 | trimSuffix "-" }}
{{- end}}

{{- define "dashboard-project.port-scanner.cluster-role-name" -}}
{{- default "dashboard-port-scanner" .Values.portscanner.clusterRole.name | trunc 63 | trimSuffix "-" }}
{{- end}}

{{- define "dashboard-project.port-scanner.cluster-role-binding-name" -}}
{{- default "dashboard-port-scanner" .Values.portscanner.clusterRoleBinding.name | trunc 63 | trimSuffix "-" }}
{{- end}}

{{/*
Defining Namespace
*/}}

{{- define "dashboard-project.tools.namespace" -}}
{{- default "dashboard" .Values.tools.namespace | trunc 63 | trimSuffix "-" }}
{{- end}}


{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "dashboard-project.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "dashboard-project.labels" -}}
helm.sh/chart: {{ include "dashboard-project.chart" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}
