{{/*
Defining resources names
*/}}

{{- define "elastic-project.elasticsearch.name" -}}
{{- default "dashboard-elasticsearch" .Values.elasticsearch.name | trunc 63 | trimSuffix "-" }}
{{- end}}


{{- define "elastic-project.kibana.name" -}}
{{- default "dashboard-kibana" .Values.kibana.name | trunc 63 | trimSuffix "-" }}
{{- end}}


{{/*
Defining Namespace
*/}}

{{- define "elastic-project.operator.namespace" -}}
{{- default "elastic-system" .Values.operator.namespace | trunc 63 | trimSuffix "-" }}
{{- end}}

{{- define "elastic-project.tools.namespace" -}}
{{- default "elastic" .Values.tools.namespace | trunc 63 | trimSuffix "-" }}
{{- end}}


{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "elastic-project.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "elastic-project.labels" -}}
helm.sh/chart: {{ include "elastic-project.chart" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}
