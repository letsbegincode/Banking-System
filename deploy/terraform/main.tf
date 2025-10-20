terraform {
  required_version = ">= 1.3.0"
  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.22"
    }
  }
}

provider "kubernetes" {
  host                   = var.kube_host
  token                  = var.kube_token
  cluster_ca_certificate = base64decode(var.kube_ca_certificate)
}

resource "kubernetes_namespace" "banking" {
  metadata {
    name = var.namespace
  }
}

resource "kubernetes_secret" "banking_config" {
  metadata {
    name      = "banking-config"
    namespace = kubernetes_namespace.banking.metadata[0].name
  }

  data = {
    BANKING_DB_USER     = base64encode(var.db_username)
    BANKING_DB_PASSWORD = base64encode(var.db_password)
  }
}

resource "kubernetes_deployment" "api" {
  metadata {
    name      = "banking-api"
    namespace = kubernetes_namespace.banking.metadata[0].name
    labels = {
      app = "banking-api"
    }
  }

  spec {
    replicas = var.api_replicas

    selector {
      match_labels = {
        app = "banking-api"
      }
    }

    template {
      metadata {
        labels = {
          app = "banking-api"
        }
      }

      spec {
        container {
          name  = "api"
          image = var.api_image

          port {
            container_port = 8080
          }

          env {
            name  = "BANKING_STORAGE_MODE"
            value = "jdbc"
          }

          env {
            name  = "BANKING_JDBC_URL"
            value = var.jdbc_url
          }

          env {
            name  = "BANKING_API_PORT"
            value = "8080"
          }

          env {
            name = "BANKING_DB_USER"

            value_from {
              secret_key_ref {
                name = kubernetes_secret.banking_config.metadata[0].name
                key  = "BANKING_DB_USER"
              }
            }
          }

          env {
            name = "BANKING_DB_PASSWORD"

            value_from {
              secret_key_ref {
                name = kubernetes_secret.banking_config.metadata[0].name
                key  = "BANKING_DB_PASSWORD"
              }
            }
          }

          resources {
            limits = var.api_resources.limits
            requests = var.api_resources.requests
          }

          readiness_probe {
            http_get {
              path = "/health"
              port = 8080
            }

            initial_delay_seconds = 10
            period_seconds        = 15
          }

          liveness_probe {
            http_get {
              path = "/health"
              port = 8080
            }

            initial_delay_seconds = 30
            period_seconds        = 20
          }

        }
      }
    }
  }
}

resource "kubernetes_service" "api" {
  metadata {
    name      = "banking-api"
    namespace = kubernetes_namespace.banking.metadata[0].name
    labels = {
      app = "banking-api"
    }
  }

  spec {
    selector = {
      app = "banking-api"
    }

    port {
      name        = "http"
      port        = 80
      target_port = 8080
    }
  }
}

resource "kubernetes_horizontal_pod_autoscaler_v2" "api" {
  metadata {
    name      = "banking-api"
    namespace = kubernetes_namespace.banking.metadata[0].name
  }

  spec {
    scale_target_ref {
      api_version = "apps/v1"
      kind        = "Deployment"
      name        = kubernetes_deployment.api.metadata[0].name
    }

    min_replicas = var.api_replicas
    max_replicas = var.api_max_replicas

    metric {
      type = "Resource"

      resource {
        name = "cpu"

        target {
          type               = "Utilization"
          average_utilization = var.api_cpu_target_utilization
        }
      }
    }
  }
}

resource "kubernetes_cron_job_v1" "console_maintenance" {
  metadata {
    name      = "banking-console-maintenance"
    namespace = kubernetes_namespace.banking.metadata[0].name
  }

  spec {
    schedule = var.console_schedule

    job_template {
      spec {
        template {
          spec {
            restart_policy = "OnFailure"

            container {
              name    = "console"
              image   = var.console_image
              command = ["java", "-jar", "/opt/app/banking-console.jar"]

              env {
                name  = "BANKING_STORAGE_MODE"
                value = "jdbc"
              }

              env {
                name  = "BANKING_JDBC_URL"
                value = var.jdbc_url
              }

              env {
                name = "BANKING_DB_USER"

                value_from {
                  secret_key_ref {
                    name = kubernetes_secret.banking_config.metadata[0].name
                    key  = "BANKING_DB_USER"
                  }
                }
              }

              env {
                name = "BANKING_DB_PASSWORD"

                value_from {
                  secret_key_ref {
                    name = kubernetes_secret.banking_config.metadata[0].name
                    key  = "BANKING_DB_PASSWORD"
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
