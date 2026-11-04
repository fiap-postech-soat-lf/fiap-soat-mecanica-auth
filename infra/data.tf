data "aws_caller_identity" "current" {}

# Publicados pelo -k8s (cluster.yml) e pelo infra/ do Identity (ADR-F4-14). Sem eles o
# cd.yml nem chega aqui: o porteiro pula a borda.
data "aws_ssm_parameter" "nlb_dns" {
  name = "/mecanica/k8s/nlb-dns"
}

data "aws_ssm_parameter" "jwt_public_key" {
  name = "/mecanica/identity/jwt-public-key"
}
