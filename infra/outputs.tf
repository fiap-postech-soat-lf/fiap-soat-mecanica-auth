output "api_url" {
  value       = aws_apigatewayv2_api.this.api_endpoint
  description = "URL base da API publica (publicada tambem em /mecanica/auth/api-url)."
}

output "authorizer_function_name" {
  value       = aws_lambda_function.authorizer.function_name
  description = "Nome da Lambda Authorizer."
}
