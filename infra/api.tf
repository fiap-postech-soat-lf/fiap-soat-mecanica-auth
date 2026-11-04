# HTTP API publica -> NLB (HTTP) -> Traefik (ADR-F4-06). Duas integracoes (F2-g): a
# "publica" remove os x-user-* que vierem do cliente; a "protegida" os preenche com o
# contexto do authorizer. O HTTP API escolhe a rota mais especifica, entao ANY /{proxy+}
# (protegida) so pega o que nao for rota publica.

locals {
  nlb_url = "http://${data.aws_ssm_parameter.nlb_dns.insecure_value}"

  rotas_publicas = concat(
    ["POST /auth/login", "POST /auth/cpf", "GET /ordem-servicos/veiculo/placa/{placa}", "POST /webhooks/mercadopago"],
    flatten([for s in var.servicos : [
      "GET /${s}/actuator/health", "GET /${s}/actuator/health/{proxy+}",
      "GET /${s}/swagger-ui.html", "GET /${s}/swagger-ui/{proxy+}",
      "GET /${s}/v3/api-docs", "GET /${s}/v3/api-docs/{proxy+}",
    ]])
  )

  parametros_comuns = {
    "overwrite:path"                    = "$request.path"
    "overwrite:header.x-correlation-id" = "$context.requestId"
  }
}

resource "aws_apigatewayv2_api" "this" {
  name          = "mecanica-api"
  protocol_type = "HTTP"
  description   = "Entrada publica da Oficina Mecanica (Fase 4)"
}

resource "aws_cloudwatch_log_group" "api" {
  name              = "/aws/apigateway/mecanica-api"
  retention_in_days = 7
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.this.id
  name        = "$default"
  auto_deploy = true

  default_route_settings {
    throttling_burst_limit = 50
    throttling_rate_limit  = 25
  }

  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.api.arn
    format = jsonencode({
      requestId               = "$context.requestId"
      requestTime             = "$context.requestTime"
      httpMethod              = "$context.httpMethod"
      path                    = "$context.path"
      routeKey                = "$context.routeKey"
      status                  = "$context.status"
      responseLength          = "$context.responseLength"
      authorizerError         = "$context.authorizer.error"
      integrationErrorMessage = "$context.integrationErrorMessage"
      integrationLatency      = "$context.integrationLatency"
    })
  }
}

# Sem o header Authorization, o API Gateway responde 401 sem invocar a Lambda; com token
# negado (isAuthorized=false), 403. A decisao fica em cache por header.
resource "aws_apigatewayv2_authorizer" "jwt" {
  api_id                            = aws_apigatewayv2_api.this.id
  name                              = "mecanica-jwt"
  authorizer_type                   = "REQUEST"
  authorizer_uri                    = aws_lambda_function.authorizer.invoke_arn
  authorizer_payload_format_version = "2.0"
  enable_simple_responses           = true
  identity_sources                  = ["$request.header.Authorization"]
  authorizer_result_ttl_in_seconds  = var.authorizer_cache_ttl
}

resource "aws_lambda_permission" "api_gateway" {
  statement_id  = "AllowApiGatewayInvokeAuthorizer"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.authorizer.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.this.execution_arn}/authorizers/${aws_apigatewayv2_authorizer.jwt.id}"
}

resource "aws_apigatewayv2_integration" "publica" {
  api_id               = aws_apigatewayv2_api.this.id
  integration_type     = "HTTP_PROXY"
  integration_method   = "ANY"
  integration_uri      = local.nlb_url
  timeout_milliseconds = 29000

  # Rota publica: o cliente nao injeta identidade.
  request_parameters = merge(local.parametros_comuns, {
    "remove:header.x-user-sub"            = "''"
    "remove:header.x-user-principal-type" = "''"
    "remove:header.x-user-cargo"          = "''"
  })
}

resource "aws_apigatewayv2_integration" "protegida" {
  api_id               = aws_apigatewayv2_api.this.id
  integration_type     = "HTTP_PROXY"
  integration_method   = "ANY"
  integration_uri      = local.nlb_url
  timeout_milliseconds = 29000

  # Sobrescreve qualquer x-user-* do cliente com o contexto do authorizer.
  request_parameters = merge(local.parametros_comuns, {
    "overwrite:header.x-user-sub"            = "$context.authorizer.sub"
    "overwrite:header.x-user-principal-type" = "$context.authorizer.principalType"
    "overwrite:header.x-user-cargo"          = "$context.authorizer.cargo"
  })
}

resource "aws_apigatewayv2_route" "publica" {
  for_each = toset(local.rotas_publicas)

  api_id             = aws_apigatewayv2_api.this.id
  route_key          = each.value
  authorization_type = "NONE"
  target             = "integrations/${aws_apigatewayv2_integration.publica.id}"
}

resource "aws_apigatewayv2_route" "protegida" {
  api_id             = aws_apigatewayv2_api.this.id
  route_key          = "ANY /{proxy+}"
  authorization_type = "CUSTOM"
  authorizer_id      = aws_apigatewayv2_authorizer.jwt.id
  target             = "integrations/${aws_apigatewayv2_integration.protegida.id}"
}
