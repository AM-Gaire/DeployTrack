# Secrets live in SSM Parameter Store, which the instance reads at boot using
# its IAM role. Nothing sensitive is baked into an image, written into user
# data, or stored on disk -- and rotating a value means updating one parameter
# and restarting, not rebuilding anything.
#
# Parameter Store rather than Secrets Manager: SecureString parameters are free,
# Secrets Manager charges about £0.32 per secret per month. For values that
# never rotate automatically, the paid features are not worth anything here.

resource "random_password" "jwt_secret" {
  length  = 48
  special = false
}

resource "random_password" "admin" {
  length           = 24
  special          = true
  override_special = "!#%*-_="
}

resource "aws_ssm_parameter" "jwt_secret" {
  name = "/${var.project_name}/jwt-secret"
  # SecureString is encrypted at rest with a KMS key. A plain String parameter
  # would be readable by anyone with console access.
  type  = "SecureString"
  value = base64encode(random_password.jwt_secret.result)

  tags = { Name = "${var.project_name}-jwt-secret" }
}

resource "aws_ssm_parameter" "db_password" {
  name  = "/${var.project_name}/db-password"
  type  = "SecureString"
  value = random_password.db.result

  tags = { Name = "${var.project_name}-db-password" }
}

resource "aws_ssm_parameter" "admin_password" {
  name  = "/${var.project_name}/admin-password"
  type  = "SecureString"
  value = random_password.admin.result

  tags = { Name = "${var.project_name}-admin-password" }
}

# The demo account is deliberately not a secret: the login screen shows these
# credentials to anyone who visits, and the account is read-only.
resource "aws_ssm_parameter" "demo_password" {
  name  = "/${var.project_name}/demo-password"
  type  = "String"
  value = "demo-readonly"

  tags = { Name = "${var.project_name}-demo-password" }
}
