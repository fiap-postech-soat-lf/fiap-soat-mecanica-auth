variable "aws_region" {
  type        = string
  description = "Regiao AWS. O Learner Lab permite apenas us-east-1 e us-west-2."
  default     = "us-east-1"
}

variable "artifact_path" {
  type        = string
  description = "Jar shade da Lambda gerado pelo ./mvnw package (relativo a infra/)."
  default     = "../target/auth-lambda.jar"
}

variable "lab_role_name" {
  type        = string
  description = "IAM Role pre-existente da Lambda. No Learner Lab nao e possivel criar roles."
  default     = "LabRole"
}

variable "jwt_issuer" {
  type        = string
  description = "Valor exigido no claim iss (contrato jwt.md do -k8s)."
  default     = "fiap-soat-mecanica-identity"
}

variable "jwt_audience" {
  type        = string
  description = "Valor que o claim aud precisa conter (contrato jwt.md do -k8s)."
  default     = "fiap-soat-mecanica"
}

variable "authorizer_cache_ttl" {
  type        = number
  description = "Cache da decisao do authorizer por header Authorization, em segundos (0 desliga)."
  default     = 300

  validation {
    condition     = var.authorizer_cache_ttl >= 0 && var.authorizer_cache_ttl <= 3600
    error_message = "authorizer_cache_ttl deve estar entre 0 e 3600."
  }
}

variable "servicos" {
  type        = list(string)
  description = "Servicos com health e Swagger publicos em /<svc>/... (StripPrefix no Traefik)."
  default     = ["identity", "os", "billing", "execucao"]
}
