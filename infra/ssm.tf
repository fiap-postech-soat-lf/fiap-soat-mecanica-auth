# Lido pelo smoke do servico-cd e pelos scripts do -k8s (tabela 3.1 do plano da F2).
resource "aws_ssm_parameter" "api_url" {
  name           = "/mecanica/auth/api-url"
  type           = "String"
  insecure_value = aws_apigatewayv2_api.this.api_endpoint
}
