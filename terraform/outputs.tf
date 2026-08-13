output "application_url" {
  description = "Where the deployed application is reachable."
  value       = "http://${aws_eip.app.public_ip}"
}

output "backend_ecr_repository" {
  description = "Push the backend image here."
  value       = aws_ecr_repository.backend.repository_url
}

output "frontend_ecr_repository" {
  description = "Push the frontend image here."
  value       = aws_ecr_repository.frontend.repository_url
}

output "database_endpoint" {
  description = "RDS hostname. Only reachable from inside the VPC."
  value       = aws_db_instance.main.address
}

output "instance_id" {
  description = "Use with `aws ssm start-session --target <id>` to get a shell without opening SSH."
  value       = aws_instance.app.id
}

output "admin_password_command" {
  description = "Reads the generated admin password. Not output directly, so it never lands in terminal scrollback or CI logs."
  value       = "aws ssm get-parameter --name /${var.project_name}/admin-password --with-decryption --query Parameter.Value --output text --region ${var.aws_region}"
}
