# Testes do infra/ com o provider mockado: nao precisa de credencial AWS nem do jar.

mock_provider "aws" {
  # ARNs validos: o provider valida o formato de destination_arn e source_arn.
  mock_resource "aws_cloudwatch_log_group" {
    defaults = {
      arn = "arn:aws:logs:us-east-1:123456789012:log-group:mock"
    }
  }

  mock_resource "aws_apigatewayv2_api" {
    defaults = {
      execution_arn = "arn:aws:execute-api:us-east-1:123456789012:mockapi"
      api_endpoint  = "https://mockapi.execute-api.us-east-1.amazonaws.com"
    }
  }

  mock_resource "aws_lambda_function" {
    defaults = {
      arn        = "arn:aws:lambda:us-east-1:123456789012:function:mecanica-authorizer"
      invoke_arn = "arn:aws:apigateway:us-east-1:lambda:path/2015-03-31/functions/arn:aws:lambda:us-east-1:123456789012:function:mecanica-authorizer/invocations"
    }
  }

  override_data {
    target = data.aws_caller_identity.current
    values = {
      account_id = "123456789012"
    }
  }

  override_data {
    target = data.aws_ssm_parameter.nlb_dns
    values = {
      insecure_value = "mecanica-nlb-0123456789.elb.us-east-1.amazonaws.com"
    }
  }

  override_data {
    target = data.aws_ssm_parameter.jwt_public_key
    values = {
      insecure_value = "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A\n-----END PUBLIC KEY-----\n"
    }
  }
}

variables {
  # Qualquer arquivo existente serve para o filebase64sha256: o teste nao depende do build.
  artifact_path = "versions.tf"
}

# O infra/ nao declara nenhum aws_iam_role (o Academy nao deixa criar role): a Lambda usa a LabRole.
run "lambda_com_labrole_fora_da_vpc" {
  command = apply

  assert {
    condition     = aws_lambda_function.authorizer.runtime == "java21"
    error_message = "A Lambda deve rodar em java21."
  }

  assert {
    condition     = aws_lambda_function.authorizer.role == "arn:aws:iam::123456789012:role/LabRole"
    error_message = "A Lambda deve usar a LabRole da conta."
  }

  assert {
    condition     = length(aws_lambda_function.authorizer.vpc_config) == 0
    error_message = "A Lambda deve ficar fora da VPC."
  }

  assert {
    condition     = aws_lambda_function.authorizer.handler == "br.com.fiap.soat.mecanica.auth.adapters.in.JwtAuthorizerHandler::handleRequest"
    error_message = "Handler da Lambda incorreto."
  }

  assert {
    condition = (
      !strcontains(aws_lambda_function.authorizer.environment[0].variables["JWT_PUBLIC_KEY"], "\n")
      && strcontains(aws_lambda_function.authorizer.environment[0].variables["JWT_PUBLIC_KEY"], "\\n")
    )
    error_message = "JWT_PUBLIC_KEY deve ir em uma linha, com o \\n escapado."
  }

  assert {
    condition     = aws_lambda_function.authorizer.logging_config[0].log_format == "JSON"
    error_message = "Os logs da Lambda devem sair em JSON."
  }
}

run "authorizer_request_simples_com_cache" {
  command = apply

  assert {
    condition     = aws_apigatewayv2_authorizer.jwt.authorizer_type == "REQUEST"
    error_message = "O authorizer deve ser do tipo REQUEST."
  }

  assert {
    condition     = aws_apigatewayv2_authorizer.jwt.authorizer_payload_format_version == "2.0"
    error_message = "O payload do authorizer deve ser 2.0."
  }

  assert {
    condition     = aws_apigatewayv2_authorizer.jwt.enable_simple_responses
    error_message = "O authorizer deve usar a resposta simples (isAuthorized)."
  }

  assert {
    condition     = one(aws_apigatewayv2_authorizer.jwt.identity_sources) == "$request.header.Authorization"
    error_message = "A identity source deve ser so o header Authorization (ausente -> 401 sem invocar a Lambda)."
  }

  assert {
    condition     = aws_apigatewayv2_authorizer.jwt.authorizer_result_ttl_in_seconds == 300
    error_message = "O cache do authorizer deve ser de 300 s."
  }

  assert {
    condition     = endswith(aws_lambda_permission.api_gateway.source_arn, "/authorizers/${aws_apigatewayv2_authorizer.jwt.id}")
    error_message = "A permissao da Lambda deve valer so para o authorizer."
  }
}

run "rotas_publicas_sem_authorizer" {
  command = apply

  assert {
    condition     = length(aws_apigatewayv2_route.publica) == 28
    error_message = "Devem existir 28 rotas publicas (4 + 6 x 4 servicos)."
  }

  assert {
    condition = alltrue([
      for r in ["POST /auth/login", "POST /auth/cpf", "GET /ordem-servicos/veiculo/placa/{placa}", "POST /webhooks/mercadopago", "GET /identity/actuator/health", "GET /execucao/v3/api-docs/{proxy+}"] :
      contains(keys(aws_apigatewayv2_route.publica), r)
    ])
    error_message = "Faltam rotas publicas esperadas."
  }

  assert {
    condition = alltrue([
      for r in aws_apigatewayv2_route.publica : r.authorizer_id == null && r.authorization_type == "NONE"
    ])
    error_message = "Nenhuma rota publica pode ter authorizer."
  }

  assert {
    condition = alltrue([
      for r in aws_apigatewayv2_route.publica : r.target == "integrations/${aws_apigatewayv2_integration.publica.id}"
    ])
    error_message = "As rotas publicas devem usar a integracao publica."
  }
}

run "fallback_protegido" {
  command = apply

  assert {
    condition     = aws_apigatewayv2_route.protegida.route_key == "ANY /{proxy+}"
    error_message = "O fallback deve ser ANY /{proxy+}."
  }

  assert {
    condition     = aws_apigatewayv2_route.protegida.authorization_type == "CUSTOM"
    error_message = "O fallback deve exigir o authorizer (CUSTOM)."
  }

  assert {
    condition     = aws_apigatewayv2_route.protegida.authorizer_id == aws_apigatewayv2_authorizer.jwt.id
    error_message = "O fallback deve usar o authorizer mecanica-jwt."
  }

  assert {
    condition     = aws_apigatewayv2_route.protegida.target == "integrations/${aws_apigatewayv2_integration.protegida.id}"
    error_message = "O fallback deve usar a integracao protegida."
  }
}

run "integracoes_para_o_nlb" {
  command = apply

  assert {
    condition = alltrue([
      for i in [aws_apigatewayv2_integration.publica, aws_apigatewayv2_integration.protegida] :
      i.integration_type == "HTTP_PROXY"
      && i.integration_uri == "http://mecanica-nlb-0123456789.elb.us-east-1.amazonaws.com"
      && i.request_parameters["overwrite:path"] == "$request.path"
      && i.request_parameters["overwrite:header.x-correlation-id"] == "$context.requestId"
    ])
    error_message = "As duas integracoes devem ser HTTP_PROXY para o NLB, repassando o caminho e o x-correlation-id."
  }

  assert {
    condition = alltrue([
      for h in ["x-user-sub", "x-user-principal-type", "x-user-cargo"] :
      aws_apigatewayv2_integration.publica.request_parameters["remove:header.${h}"] == "''"
      && !contains(keys(aws_apigatewayv2_integration.publica.request_parameters), "overwrite:header.${h}")
    ])
    error_message = "A integracao publica deve remover os 3 x-user-* e nao preenche-los."
  }

  assert {
    condition = (
      aws_apigatewayv2_integration.protegida.request_parameters["overwrite:header.x-user-sub"] == "$context.authorizer.sub"
      && aws_apigatewayv2_integration.protegida.request_parameters["overwrite:header.x-user-principal-type"] == "$context.authorizer.principalType"
      && aws_apigatewayv2_integration.protegida.request_parameters["overwrite:header.x-user-cargo"] == "$context.authorizer.cargo"
    )
    error_message = "A integracao protegida deve preencher os x-user-* com o contexto do authorizer."
  }
}

run "publica_api_url_no_ssm" {
  command = apply

  assert {
    condition     = aws_ssm_parameter.api_url.name == "/mecanica/auth/api-url"
    error_message = "O parametro deve ser /mecanica/auth/api-url."
  }

  assert {
    condition     = aws_ssm_parameter.api_url.insecure_value == aws_apigatewayv2_api.this.api_endpoint
    error_message = "O parametro deve guardar o api_endpoint da HTTP API."
  }

  assert {
    condition     = output.api_url == aws_apigatewayv2_api.this.api_endpoint
    error_message = "O output api_url deve ser o api_endpoint."
  }
}
