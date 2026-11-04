# Configurado pelo cd.yml com -backend-config (bucket mecanica-tfstate-<accountId>,
# key fiap-soat-mecanica-auth/terraform.tfstate, region us-east-1, use_lockfile=true).
terraform {
  backend "s3" {}
}
