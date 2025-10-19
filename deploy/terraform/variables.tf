variable "kube_host" {
  description = "Kubernetes API server URL"
  type        = string
}

variable "kube_token" {
  description = "Kubernetes service account token"
  type        = string
  sensitive   = true
}

variable "kube_ca_certificate" {
  description = "Base64 encoded cluster CA certificate"
  type        = string
}

variable "namespace" {
  description = "Namespace to deploy resources"
  type        = string
  default     = "banking-system"
}

variable "api_replicas" {
  description = "Minimum number of API replicas"
  type        = number
  default     = 2
}

variable "api_max_replicas" {
  description = "Maximum number of API replicas"
  type        = number
  default     = 5
}

variable "api_cpu_target_utilization" {
  description = "CPU utilization target for HPA"
  type        = number
  default     = 60
}

variable "api_resources" {
  description = "Resource requests and limits for API pods"
  type = object({
    requests = map(string)
    limits   = map(string)
  })
  default = {
    requests = {
      cpu    = "100m"
      memory = "256Mi"
    }
    limits = {
      cpu    = "500m"
      memory = "512Mi"
    }
  }
}

variable "api_image" {
  description = "Container image for the API deployment"
  type        = string
}

variable "console_image" {
  description = "Container image for the console CronJob"
  type        = string
}

variable "console_schedule" {
  description = "Cron schedule for console maintenance job"
  type        = string
  default     = "0 1 * * *"
}

variable "jdbc_url" {
  description = "JDBC URL for the banking database"
  type        = string
}

variable "db_username" {
  description = "Database username for application connections"
  type        = string
}

variable "db_password" {
  description = "Database password for application connections"
  type        = string
  sensitive   = true
}

variable "api_key" {
  description = "API key used to authenticate HTTP requests"
  type        = string
  sensitive   = true
}
