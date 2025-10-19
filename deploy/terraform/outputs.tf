output "namespace" {
  description = "Namespace hosting the banking workloads"
  value       = kubernetes_namespace.banking.metadata[0].name
}

output "api_service_name" {
  description = "Name of the Kubernetes service exposing the API"
  value       = kubernetes_service.api.metadata[0].name
}

output "persistent_volume_claim" {
  description = "Stateful volume claim backing both console and API"
  value       = kubernetes_persistent_volume_claim.bank_state.metadata[0].name
}
