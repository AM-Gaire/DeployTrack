terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source = "hashicorp/aws"
      # Pinned to a major version. Without a constraint, a future provider
      # release with breaking changes would be picked up silently on the next
      # `terraform init`.
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # State is local by default, which is fine for one person: terraform.tfstate
  # stays on this machine and is gitignored, because it contains the generated
  # database and JWT passwords in plaintext.
  #
  # A team would move this to an S3 backend with DynamoDB locking so state is
  # shared and two people cannot apply at once. Deliberately not done here --
  # it would add a bucket and a table to pay for and manage for a single
  # operator who cannot conflict with themselves.
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project   = var.project_name
      ManagedBy = "terraform"
    }
  }
}
