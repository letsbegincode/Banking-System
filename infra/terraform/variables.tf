variable "aws_region" {
  description = "AWS region to deploy the infrastructure"
  type        = string
  default     = "us-east-1"
}

variable "kube_host" {
  description = "Kubernetes API server endpoint"
  type        = string
}

variable "kube_ca" {
  description = "Base64 encoded Kubernetes cluster CA"
  type        = string
}

variable "kube_token" {
  description = "Service account token used by Terraform to administer the cluster"
  type        = string
  sensitive   = true
}

variable "vpc_cidr_block" {
  description = "CIDR block for the application VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "cache_instance_type" {
  description = "Instance class for the Redis cache cluster"
  type        = string
  default     = "cache.t3.micro"
}

variable "cache_engine_version" {
  description = "Redis engine version"
  type        = string
  default     = "7.0"
}

variable "broker_type" {
  description = "Message broker to provision (kafka|rabbitmq)"
  type        = string
  default     = "kafka"
}

variable "cluster_name" {
  description = "EKS cluster name"
  type        = string
  default     = "banking-platform"
}

variable "node_group_size" {
  description = "Number of worker nodes in the default node group"
  type        = number
  default     = 3
}
