namespace                   = "banking-production"
api_image                   = "ghcr.io/acme/banking-api:prod"
console_image               = "ghcr.io/acme/banking-console:prod"
api_replicas                = 4
api_max_replicas            = 10
api_cpu_target_utilization  = 50
jdbc_url                    = "jdbc:mysql://prod-banking.cluster.internal:3306/banking?useSSL=true&requireSSL=true&serverTimezone=UTC"
db_username                 = "banking_app"
db_password                 = ""
console_schedule            = "0 */6 * * *"
