locals {
  lab_role_arn = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/${var.lab_role_name}"
}

resource "aws_cloudwatch_log_group" "authorizer" {
  name              = "/aws/lambda/mecanica-authorizer"
  retention_in_days = 7
}

# Fora da VPC: so valida o token com a chave publica, sem chamar nenhum servico (F2-c).
resource "aws_lambda_function" "authorizer" {
  function_name    = "mecanica-authorizer"
  description      = "Lambda Authorizer: valida o JWT RS256 emitido pelo Identity"
  role             = local.lab_role_arn
  runtime          = "java21"
  handler          = "br.com.fiap.soat.mecanica.auth.adapters.in.JwtAuthorizerHandler::handleRequest"
  filename         = var.artifact_path
  source_code_hash = filebase64sha256(var.artifact_path)
  memory_size      = 512
  timeout          = 5

  environment {
    variables = {
      # Variavel de ambiente com PEM em uma linha: o ChavePublicaPem aceita o \n escapado.
      JWT_PUBLIC_KEY = replace(data.aws_ssm_parameter.jwt_public_key.insecure_value, "\n", "\\n")
      JWT_ISSUER     = var.jwt_issuer
      JWT_AUDIENCE   = var.jwt_audience
    }
  }

  logging_config {
    log_format = "JSON"
    log_group  = aws_cloudwatch_log_group.authorizer.name
  }
}
