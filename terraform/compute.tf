resource "aws_ecr_repository" "backend" {
  name                 = "${var.project_name}-backend"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    # Scans each pushed image against known CVEs. Free, and the results are
    # the difference between finding a vulnerable base image now or after it
    # is deployed.
    scan_on_push = true
  }

  # Lets `terraform destroy` remove the repository even when it still holds
  # images, rather than failing and leaving a billable resource behind.
  force_delete = true

  tags = { Name = "${var.project_name}-backend" }
}

resource "aws_ecr_repository" "frontend" {
  name                 = "${var.project_name}-frontend"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  force_delete = true

  tags = { Name = "${var.project_name}-frontend" }
}

# ECR storage is only free up to 500MB, and these images total roughly 570MB.
# Keeping the ten most recent images stops every merge accumulating forever.
resource "aws_ecr_lifecycle_policy" "backend" {
  repository = aws_ecr_repository.backend.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the last 10 images"
      selection    = { tagStatus = "any", countType = "imageCountMoreThan", countNumber = 10 }
      action       = { type = "expire" }
    }]
  })
}

resource "aws_ecr_lifecycle_policy" "frontend" {
  repository = aws_ecr_repository.frontend.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the last 10 images"
      selection    = { tagStatus = "any", countType = "imageCountMoreThan", countNumber = 10 }
      action       = { type = "expire" }
    }]
  })
}

# The instance assumes this role instead of holding AWS access keys. Keys on a
# server are permanent until someone rotates them, and they leak through logs,
# backups and images; role credentials are temporary and rotate automatically.
resource "aws_iam_role" "instance" {
  name = "${var.project_name}-instance"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = { Name = "${var.project_name}-instance-role" }
}

resource "aws_iam_role_policy" "instance" {
  name = "${var.project_name}-instance"
  role = aws_iam_role.instance.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ecr:GetAuthorizationToken",
          "ecr:BatchGetImage",
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchCheckLayerAvailability",
        ]
        # GetAuthorizationToken is account-wide by design and cannot be scoped
        # to a repository, so it is granted here and the pull actions below are
        # what actually restrict access.
        Resource = "*"
      },
      {
        Effect   = "Allow"
        Action   = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"]
        # Scoped to this project's parameters only. A wildcard here would let a
        # compromised instance read every secret in the account.
        Resource = "arn:aws:ssm:${var.aws_region}:${data.aws_caller_identity.current.account_id}:parameter/${var.project_name}/*"
      },
      {
        Effect   = "Allow"
        Action   = ["kms:Decrypt"]
        Resource = "*"
        Condition = {
          StringEquals = { "kms:ViaService" = "ssm.${var.aws_region}.amazonaws.com" }
        }
      },
    ]
  })
}

# Lets you open a shell on the instance through Session Manager instead of
# opening port 22 to the internet. No SSH key to lose, and every session is
# logged in CloudTrail.
resource "aws_iam_role_policy_attachment" "ssm_core" {
  role       = aws_iam_role.instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "instance" {
  name = "${var.project_name}-instance"
  role = aws_iam_role.instance.name
}

data "aws_caller_identity" "current" {}

# Resolved at apply time rather than hardcoded: AMI ids differ per region and
# change whenever Amazon publishes a patched image.
data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-arm64"]
  }
}

resource "aws_instance" "app" {
  # t4g instances are ARM, which is why the AMI filter above selects arm64 and
  # the images are built for that architecture. ARM is cheaper for identical
  # performance at this size.
  ami                    = data.aws_ami.al2023.id
  instance_type          = var.instance_type
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.app.id]
  iam_instance_profile   = aws_iam_instance_profile.instance.name

  user_data = templatefile("${path.module}/user_data.sh", {
    aws_region     = var.aws_region
    account_id     = data.aws_caller_identity.current.account_id
    project_name   = var.project_name
    db_host        = aws_db_instance.main.address
    db_name        = aws_db_instance.main.db_name
    db_user        = aws_db_instance.main.username
    admin_email    = var.admin_email
  })

  # Replaces the instance when the bootstrap script changes, rather than
  # leaving a running instance configured by an older version of it.
  user_data_replace_on_change = true

  root_block_device {
    volume_size = 20
    volume_type = "gp3"
    encrypted   = true
  }

  metadata_options {
    # IMDSv2 only. Version 1 is what made SSRF vulnerabilities able to steal
    # instance role credentials -- requiring a token blocks that class of
    # attack.
    http_tokens = "required"
  }

  tags = { Name = "${var.project_name}-app" }

  depends_on = [aws_db_instance.main]
}

# A stable address that survives stopping and starting the instance. It is free
# while attached to a running instance and charged at roughly £3 a month when
# left unattached, which is why `terraform destroy` releasing it matters.
resource "aws_eip" "app" {
  instance = aws_instance.app.id
  domain   = "vpc"

  tags = { Name = "${var.project_name}-eip" }
}
