# Placeholder values; provide real cluster coordinates through environment-specific pipelines.
namespace                   = "banking-staging"
api_image                   = "ghcr.io/acme/banking-api:staging"
console_image               = "ghcr.io/acme/banking-console:staging"
api_replicas                = 2
api_max_replicas            = 4
api_cpu_target_utilization  = 55
jdbc_url                    = "jdbc:mysql://staging-banking.cluster.internal:3306/banking?useSSL=true&requireSSL=true&serverTimezone=UTC"
db_username                 = "banking_app"
db_password                 = ""
api_key                     = "staging-api-key"
console_schedule            = "0 2 * * *"
