variable "project_name" {
  description = "Prefix for every resource name, so everything this stack creates is identifiable in the console."
  type        = string
  default     = "deploytrack"
}

variable "aws_region" {
  description = "Region to deploy into. eu-west-2 is London; pick the one closest to you."
  type        = string
  default     = "eu-west-2"
}

variable "instance_type" {
  description = "EC2 size. t3.micro is free-tier eligible for 750 hours a month in the first 12 months."
  type        = string
  default     = "t3.micro"
}

variable "db_instance_class" {
  description = "RDS size. db.t3.micro is free-tier eligible for 750 hours a month in the first 12 months."
  type        = string
  default     = "db.t3.micro"
}

variable "db_allocated_storage" {
  description = "RDS storage in GB. Free tier covers 20GB; going above starts charging."
  type        = number
  default     = 20
}

variable "admin_email" {
  description = "Email for the bootstrapped admin account."
  type        = string
  default     = "admin@deploytrack.dev"
}

variable "ssh_ingress_cidr" {
  description = <<-EOT
    Which addresses may reach SSH. Defaults to nothing, because 0.0.0.0/0 exposes
    port 22 to the entire internet and every credential-stuffing bot on it.
    Set this to "YOUR.IP.ADDRESS/32" only if you need to shell in; the instance
    is otherwise reachable through Session Manager without opening a port.
  EOT
  type        = string
  default     = ""
}

variable "allowed_http_cidr" {
  description = "Which addresses may reach the application. Public by design -- this is the web app."
  type        = string
  default     = "0.0.0.0/0"
}
