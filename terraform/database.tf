resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-db-subnets"
  subnet_ids = aws_subnet.private[*].id

  tags = { Name = "${var.project_name}-db-subnets" }
}

# Generated rather than typed into a variable. A password in a .tfvars file
# gets committed sooner or later; this one exists only in Terraform state and
# in SSM Parameter Store, and nobody ever needs to read it.
resource "random_password" "db" {
  length = 32
  # RDS rejects several punctuation characters in master passwords, and the
  # error it gives is unhelpful. Restricting the set avoids that entirely.
  special          = true
  override_special = "!#%*-_=+"
}

resource "aws_db_instance" "main" {
  identifier     = "${var.project_name}-db"
  engine         = "postgres"
  engine_version = "16"
  instance_class = var.db_instance_class

  allocated_storage = var.db_allocated_storage
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = "deploytrack"
  username = "deploytrack"
  password = random_password.db.result

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.database.id]

  # The database must not be reachable from the internet. This is the single
  # most important line in this file -- a publicly accessible RDS instance is
  # found by scanners within minutes.
  publicly_accessible = false

  backup_retention_period = 7
  # A single-AZ instance is what the free tier covers. Multi-AZ doubles the
  # cost for failover this project does not need.
  multi_az = false

  # Skipping the final snapshot means `terraform destroy` actually completes
  # rather than failing and leaving a billable instance behind. Appropriate
  # here precisely because the data is disposable; on anything real this
  # should be false.
  skip_final_snapshot = true
  deletion_protection = false

  # Minor version patches apply during the maintenance window rather than
  # never, so known CVEs get fixed without manual intervention.
  auto_minor_version_upgrade = true

  tags = { Name = "${var.project_name}-db" }
}
